-- V13 — Phase 7: IN_PROGRESS lifecycle stage + HIPAA-grade actor/timestamp
-- columns on every status transition for lab_orders + radiology_orders.
--
-- HIPAA §164.312(b) requires audit controls that record + examine activity
-- in systems that contain PHI. Today we have timestamps on each transition
-- but the ACTOR (who flipped the status) is captured only on createdByName
-- + via audit_log (Phase 0). Adding per-transition actor columns gives:
--   1. fast queries ("who collected sample for order #123?") without an
--      audit_log scan
--   2. the operator UI can render "Collected at 14:32 by Jane Phlebotomist"
--      from one row read
--   3. defence-in-depth: even if audit_log writes ever drop, the actor
--      attribution survives on the row.
--
-- The Phase 5 audit_log table remains the tamper-evident source of truth;
-- these columns are denormalised projections.
--
-- IN_PROGRESS itself is an INT enum (LabStatus / RadiologyStatus); no DB
-- enum to ALTER. The value 5 lands at the END of the existing
-- {PENDING_COLLECTION=1, AWAITING_REPORT=2, REPORT_GENERATED=3, BILLED=4}
-- so no historical row needs rewriting.

-- ── lab_orders — actor stamps for every transition ───────────────────────
ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS collected_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS collected_by_name    VARCHAR(200),

    ADD COLUMN IF NOT EXISTS received_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS received_by_user_id  UUID,
    ADD COLUMN IF NOT EXISTS received_by_name     VARCHAR(200),

    ADD COLUMN IF NOT EXISTS started_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_by_user_id   UUID,
    ADD COLUMN IF NOT EXISTS started_by_name      VARCHAR(200),

    ADD COLUMN IF NOT EXISTS reported_by_user_id  UUID,
    ADD COLUMN IF NOT EXISTS reported_by_name     VARCHAR(200);

-- ── radiology_orders — same shape, naming mirrors scanned_at not collected_at
ALTER TABLE radiology_orders
    ADD COLUMN IF NOT EXISTS scanned_by_user_id   UUID,
    ADD COLUMN IF NOT EXISTS scanned_by_name      VARCHAR(200),

    ADD COLUMN IF NOT EXISTS received_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS received_by_user_id  UUID,
    ADD COLUMN IF NOT EXISTS received_by_name     VARCHAR(200),

    ADD COLUMN IF NOT EXISTS started_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_by_user_id   UUID,
    ADD COLUMN IF NOT EXISTS started_by_name      VARCHAR(200),

    ADD COLUMN IF NOT EXISTS reported_by_user_id  UUID,
    ADD COLUMN IF NOT EXISTS reported_by_name     VARCHAR(200);

-- ── Helpful indexes for "who did what today" queries ────────────────────
CREATE INDEX IF NOT EXISTS idx_lab_orders_started_at
    ON lab_orders (hospital_id, started_at DESC)
    WHERE started_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_radiology_orders_started_at
    ON radiology_orders (hospital_id, started_at DESC)
    WHERE started_at IS NOT NULL;

COMMENT ON COLUMN lab_orders.collected_by_user_id IS
    'HIPAA audit: who marked the sample collected. Triple (collected_at + collected_by_user_id + collected_by_name) lets the operator UI render attribution without joining audit_log.';
COMMENT ON COLUMN lab_orders.received_at IS
    'When the lab receiving desk took custody. Per-order denormalisation of the existing lab_specimen.received_at — easier to query "orders awaiting receive" without joining specimens.';
COMMENT ON COLUMN lab_orders.started_at IS
    'When the bench tech moved the order to IN_PROGRESS (analyser run started). HIPAA actor: started_by_user_id + started_by_name.';
COMMENT ON COLUMN radiology_orders.started_at IS
    'When the radiology tech started the modality run. HIPAA actor: started_by_user_id + started_by_name.';
