-- V3 — Phase 1: per-container specimen tracking.
--
-- Today a lab_order has a single free-text `sample_type` column, which is
-- enough for "is this a blood sample?" but loses chain-of-custody:
--   * we can't tell when the sample was drawn vs received in the lab,
--   * we can't barcode-track a tube,
--   * we can't reject a sample with a reason code,
--   * we can't split an order into multiple tubes (CBC + LFT from same draw).
--
-- This table models ONE physical container per row. A lab_order can have N
-- specimens (a patient with CBC + LFT + Lipid is one order with 3 tubes), and
-- each specimen tracks its own collected_at / received_at / accessioned_at /
-- rejection state.
--
-- Backward compat: lab_order.sample_type is kept. Existing flows that hit
-- /api/lab/{id}/collect still work without ever inserting a specimen row.
-- LabService.markCollected gets a hook in Phase 1c to create a default
-- specimen row when none exists, so the audit trail starts populating
-- automatically for in-flight orders.

CREATE TABLE IF NOT EXISTS lab_specimen (
    id                      BIGSERIAL PRIMARY KEY,
    lab_order_id            BIGINT NOT NULL REFERENCES lab_orders(id) ON DELETE CASCADE,
    hospital_id             UUID NOT NULL,

    -- Physical container metadata
    container_type          VARCHAR(50),    -- EDTA | CITRATE | HEPARIN | PLAIN | FLUORIDE | URINE_CUP | STOOL_CUP | SWAB | OTHER
    additive                VARCHAR(50),    -- redundant when container_type implies it; kept for non-standard kits
    volume_ml               DECIMAL(6,2),

    -- Identification
    barcode                 VARCHAR(64),
    qr_payload              VARCHAR(255),

    -- Chain of custody — null until the corresponding event happens
    collected_at            TIMESTAMP,
    collected_by_user_id    UUID,
    collected_by_name       VARCHAR(200),
    collection_site         VARCHAR(100),

    received_at             TIMESTAMP,
    received_by_user_id     UUID,
    transport_temperature_c DECIMAL(4,1),

    accessioned_at          TIMESTAMP,
    accessioned_by_user_id  UUID,

    -- Rejection (controlled vocab — see V4 lab_rejection_reason)
    rejected                BOOLEAN NOT NULL DEFAULT FALSE,
    rejected_at             TIMESTAMP,
    rejected_by_user_id     UUID,
    rejection_reason_code   VARCHAR(50),
    rejection_notes         TEXT,

    -- Storage + retention
    storage_location        VARCHAR(100),
    discard_at              TIMESTAMP,

    notes                   TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

-- Barcode is unique when set (some workflows still operate without one).
CREATE UNIQUE INDEX IF NOT EXISTS uq_lab_specimen_barcode
    ON lab_specimen (barcode) WHERE barcode IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lab_specimen_order
    ON lab_specimen (lab_order_id);

CREATE INDEX IF NOT EXISTS idx_lab_specimen_hospital_status
    ON lab_specimen (hospital_id, rejected, created_at DESC);

COMMENT ON TABLE lab_specimen IS
    'One physical container per row. A lab order can have many specimens (one per analyte panel / additive).';
COMMENT ON COLUMN lab_specimen.container_type IS
    'EDTA | CITRATE | HEPARIN | PLAIN | FLUORIDE | URINE_CUP | STOOL_CUP | SWAB | OTHER';
COMMENT ON COLUMN lab_specimen.rejection_reason_code IS
    'FK-style reference to lab_rejection_reason.code (not enforced — keep the seed authoritative but allow custom codes).';
