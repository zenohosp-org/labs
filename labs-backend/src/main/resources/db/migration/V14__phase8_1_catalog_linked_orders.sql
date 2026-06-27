-- V14 — Phase 8.1: catalog-linked orders.
--
-- Closes the gap where order-create accepts only free-text serviceName, with
-- no FK back to the lab_services catalogue. After this migration:
--
--   * lab_orders.lab_service_id        — nullable FK → lab_services(id)
--   * radiology_orders.lab_service_id  — nullable FK → lab_services(id)
--   * service_name_mapping_status      — VARCHAR(20) on both tables
--   * radiology_orders.gst_rate        — NUMERIC(5,2) for parity with lab_orders
--                                        (lets catalog-snapshotted GST flow into
--                                        the radiology auto-bill path)
--
-- Nullable FK + serviceName snapshot preserved = full back-compat: HMS keeps
-- sending free-text during the cutover window, only NEW orders carry the FK.
-- ON DELETE SET NULL so a future catalogue prune doesn't cascade-wipe history.
--
-- service_name_mapping_status is admin-triage metadata:
--   matched         — request carried labServiceId OR backfill auto-resolved
--   ambiguous       — multiple catalogue rows could match (CT Scan, MRI Scan, ...)
--                     admin must reassign in the labs UI
--   invalid         — order was created for a non-investigation
--                     (e.g. "General Consultation" landing in radiology_orders)
--   legacy-misroute — pathology table holds a radiology test
--                     (the "MRI Test" rows in lab_orders) — terminal-state
--                     records that must not be deleted (HIPAA) but must not
--                     route into active queues either
--
-- Backfill section is idempotent: each UPDATE is guarded so re-run is safe.

-- ──────────────────────────────────────────────
-- Schema additions
-- ──────────────────────────────────────────────

ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS lab_service_id               BIGINT,
    ADD COLUMN IF NOT EXISTS service_name_mapping_status  VARCHAR(20);

ALTER TABLE lab_orders
    DROP CONSTRAINT IF EXISTS fk_lab_orders_lab_service;

ALTER TABLE lab_orders
    ADD CONSTRAINT fk_lab_orders_lab_service
    FOREIGN KEY (lab_service_id)
    REFERENCES lab_services(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_lab_orders_lab_service ON lab_orders(lab_service_id)
    WHERE lab_service_id IS NOT NULL;

ALTER TABLE radiology_orders
    ADD COLUMN IF NOT EXISTS lab_service_id               BIGINT,
    ADD COLUMN IF NOT EXISTS service_name_mapping_status  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS gst_rate                     NUMERIC(5,2);

ALTER TABLE radiology_orders
    DROP CONSTRAINT IF EXISTS fk_radiology_orders_lab_service;

ALTER TABLE radiology_orders
    ADD CONSTRAINT fk_radiology_orders_lab_service
    FOREIGN KEY (lab_service_id)
    REFERENCES lab_services(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_radiology_orders_lab_service ON radiology_orders(lab_service_id)
    WHERE lab_service_id IS NOT NULL;

-- mapping_status CHECK — bounded enum, NULL allowed (legacy + new free-text rows
-- without any classification yet).
ALTER TABLE lab_orders
    DROP CONSTRAINT IF EXISTS ck_lab_orders_mapping_status;
ALTER TABLE lab_orders
    ADD CONSTRAINT ck_lab_orders_mapping_status
    CHECK (service_name_mapping_status IS NULL
        OR service_name_mapping_status IN ('matched','ambiguous','invalid','legacy-misroute'));

ALTER TABLE radiology_orders
    DROP CONSTRAINT IF EXISTS ck_radiology_orders_mapping_status;
ALTER TABLE radiology_orders
    ADD CONSTRAINT ck_radiology_orders_mapping_status
    CHECK (service_name_mapping_status IS NULL
        OR service_name_mapping_status IN ('matched','ambiguous','invalid','legacy-misroute'));

COMMENT ON COLUMN lab_orders.lab_service_id IS
    'Phase 8.1 FK to lab_services. Nullable for back-compat with free-text orders.';
COMMENT ON COLUMN lab_orders.service_name_mapping_status IS
    'Admin-triage classification — see V14 migration header for the enum.';
COMMENT ON COLUMN radiology_orders.lab_service_id IS
    'Phase 8.1 FK to lab_services. Nullable for back-compat with free-text orders.';
COMMENT ON COLUMN radiology_orders.service_name_mapping_status IS
    'Admin-triage classification — see V14 migration header for the enum.';
COMMENT ON COLUMN radiology_orders.gst_rate IS
    'GST % snapshot at order time (e.g. 18 for 18%). Parity with lab_orders.gst_rate.';

-- ──────────────────────────────────────────────
-- Backfill — auto-resolve where possible, classify the rest for admin triage.
-- ──────────────────────────────────────────────

-- 1. RADIOLOGY auto-match by exact (case-insensitive, trimmed) name within the
--    same hospital tenant. Today this resolves exactly 1 row of 16
--    (rad #16 'X-Ray Chest PA' → RAD-XR-CHEST at Vasantha).
UPDATE radiology_orders r
   SET lab_service_id = ls.id,
       service_name_mapping_status = 'matched'
  FROM lab_services ls
 WHERE r.lab_service_id IS NULL
   AND ls.hospital_id = r.hospital_id
   AND ls.discipline  = 'RADIOLOGY'
   AND lower(trim(r.service_name)) = lower(ls.name);

-- 2. RADIOLOGY flag ambiguous — multiple catalogue rows could match these names.
UPDATE radiology_orders
   SET service_name_mapping_status = 'ambiguous'
 WHERE lab_service_id IS NULL
   AND service_name_mapping_status IS NULL
   AND lower(trim(service_name)) IN ('ct scan','mri scan','x-ray','xray','ultrasound','usg','mri','ct');

-- 3. RADIOLOGY flag invalid — non-investigations that landed in this table.
UPDATE radiology_orders
   SET service_name_mapping_status = 'invalid'
 WHERE lab_service_id IS NULL
   AND service_name_mapping_status IS NULL
   AND lower(trim(service_name)) IN ('general consultation','consultation','followup','follow-up');

-- 4. LAB auto-match by exact name within tenant + PATHOLOGY/CYTOLOGY/HISTOPATH.
UPDATE lab_orders o
   SET lab_service_id = ls.id,
       service_name_mapping_status = 'matched'
  FROM lab_services ls
 WHERE o.lab_service_id IS NULL
   AND ls.hospital_id = o.hospital_id
   AND ls.discipline IN ('PATHOLOGY','CYTOLOGY','HISTOPATHOLOGY')
   AND lower(trim(o.service_name)) = lower(ls.name);

-- 5. LAB flag legacy-misroute — radiology tests filed in the lab pipeline.
--    Today this catches the 5 'MRI Test' orders at Vasantha (ids 11-15);
--    cannot be deleted (3 are reported, status=BILLED) — flagged so admin
--    triage can exclude them from active queues going forward.
UPDATE lab_orders
   SET service_name_mapping_status = 'legacy-misroute'
 WHERE lab_service_id IS NULL
   AND service_name_mapping_status IS NULL
   AND (lower(trim(service_name)) LIKE 'mri%'
        OR lower(trim(service_name)) LIKE 'ct scan%'
        OR lower(trim(service_name)) LIKE 'x-ray%'
        OR lower(trim(service_name)) LIKE 'ultrasound%'
        OR lower(trim(service_name)) LIKE 'usg%'
        OR lower(trim(service_name)) LIKE 'mammogra%'
        OR lower(trim(service_name)) LIKE '%scan');

-- 6. Sanity: leave anything else NULL. The application will fill mapping_status
--    on future orders (matched when labServiceId is passed; null otherwise).
