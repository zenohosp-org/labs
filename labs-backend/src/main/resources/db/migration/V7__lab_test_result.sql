-- V7 — Phase 2: per-analyte result rows.
--
-- Replaces the single `findings`/`observation` blob on lab_orders with one
-- row per analyte. A CBC order now generates ~12 result rows (WBC, RBC, Hb,
-- Hct, MCV, MCH, MCHC, Platelets, Neutrophils %, Lymphocytes %, ...) each
-- with its own value, unit, abnormal flag, panic flag, delta, and signoff
-- attribution.
--
-- Backward compat: lab_orders.findings/observation STAY. The old report
-- viewer keeps rendering them. The new viewer (Phase 2 frontend later)
-- prefers per-analyte rows when present, falls back to the blob otherwise.
-- LabService.generateReport still works unchanged — it just stops being
-- the only way to capture results.
--
-- State machine (per result):
--   PENDING ─→ PRELIMINARY ─→ FINAL ─→ (optional) CORRECTED
--                                   ↓
--                              CANCELLED
--
-- Authorisation (pathologist sign-off, NABL requirement for some
-- disciplines) is orthogonal to the FSM — a result is FINAL once the tech
-- verifies it, and gains an authorised_by stamp when the pathologist
-- signs. lab_test_catalog.requires_authorisation drives which disciplines
-- need that second step.
--
-- Amendments are immutable: editing a FINAL result creates a NEW row with
-- amendment_of_id pointing at the original. Both rows coexist; the
-- original keeps its status. NABL audit trail demands this.

CREATE TABLE IF NOT EXISTS lab_test_result (
    id                        BIGSERIAL    PRIMARY KEY,
    lab_order_id              BIGINT       NOT NULL REFERENCES lab_orders(id) ON DELETE CASCADE,
    specimen_id               BIGINT       REFERENCES lab_specimen(id),     -- nullable for legacy / non-specimen-tracked entries
    hospital_id               UUID         NOT NULL,

    -- Test identity
    test_code                 VARCHAR(50)  NOT NULL,                        -- matches lab_test_catalog.test_code when catalog row exists
    analyte_name              VARCHAR(200) NOT NULL,                        -- denormalised for display when catalog row absent
    loinc_code                VARCHAR(20),

    -- The result
    value_numeric             DECIMAL(18,6),                                -- for NUMERIC tests
    value_text                TEXT,                                         -- for TEXT / CODED / RATIO tests
    unit                      VARCHAR(50),                                  -- UCUM
    method                    VARCHAR(80),                                  -- e.g. "Spectrophotometry"
    instrument_id             VARCHAR(80),                                  -- analyzer identifier; free text for Phase 2 (Phase 6 builds the instrument table)
    reagent_lot               VARCHAR(80),

    -- Reference + flags
    reference_low             DECIMAL(18,6),                                -- snapshot of the matched reference range at result-entry time
    reference_high            DECIMAL(18,6),                                -- (so changing the catalogue later doesn't retro-flag old results)
    reference_text            VARCHAR(200),
    abnormal_flag             VARCHAR(4),                                   -- HL7 OBX-8: N | L | H | LL | HH | A | AA
    panic_flag                BOOLEAN      NOT NULL DEFAULT FALSE,          -- TRUE when value crosses critical_low/critical_high
    delta_from_previous       DECIMAL(18,6),                                -- value_numeric minus the patient's previous final result for this test
    delta_check_flag          VARCHAR(20),                                  -- NONE | SIGNIFICANT | INSIGNIFICANT (Phase 2 leaves rule config minimal)

    -- Lifecycle
    result_status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',      -- PENDING | PRELIMINARY | FINAL | CORRECTED | CANCELLED
    entered_by_user_id        UUID,
    entered_by_name           VARCHAR(200),
    entered_at                TIMESTAMP,
    verified_by_user_id       UUID,
    verified_by_name          VARCHAR(200),
    verified_at               TIMESTAMP,
    authorised_by_user_id     UUID,
    authorised_by_name        VARCHAR(200),
    authorised_at             TIMESTAMP,

    -- Amendment trail
    amendment_of_id           BIGINT       REFERENCES lab_test_result(id),  -- this row supersedes the referenced row
    amendment_reason_code     VARCHAR(50),
    amendment_reason_notes    TEXT,

    -- Panic-call log (NABL: every critical result must be communicated + acked)
    panic_called_at           TIMESTAMP,
    panic_called_to           VARCHAR(200),
    panic_acknowledged_by     VARCHAR(200),
    panic_acknowledged_at     TIMESTAMP,

    comments                  TEXT,

    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lab_test_result_order
    ON lab_test_result (lab_order_id);

CREATE INDEX IF NOT EXISTS idx_lab_test_result_specimen
    ON lab_test_result (specimen_id) WHERE specimen_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lab_test_result_hospital_status
    ON lab_test_result (hospital_id, result_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lab_test_result_panic
    ON lab_test_result (hospital_id, panic_flag, created_at DESC)
    WHERE panic_flag = TRUE;

-- Delta-check + cumulative-report lookups: latest final result per patient + test_code.
-- We don't store patient_id here (it's on lab_orders) but the report viewer joins through
-- lab_orders to find prior results for the same patient. An index on (test_code, hospital_id)
-- helps that join scan stay cheap.
CREATE INDEX IF NOT EXISTS idx_lab_test_result_test_lookup
    ON lab_test_result (hospital_id, test_code, result_status);

COMMENT ON TABLE lab_test_result IS
    'Per-analyte result row. Replaces the lab_orders.findings blob for fully-itemised results.';
COMMENT ON COLUMN lab_test_result.abnormal_flag IS
    'HL7 v2 OBX-8: N (normal) | L (low) | H (high) | LL (panic low) | HH (panic high) | A (abnormal) | AA (critically abnormal).';
COMMENT ON COLUMN lab_test_result.amendment_of_id IS
    'When set, this row replaces an earlier FINAL row whose values were wrong. Both rows survive; the original keeps its status.';
COMMENT ON COLUMN lab_test_result.panic_flag IS
    'TRUE when value crossed the matched reference range''s critical_low or critical_high band. Drives the panic-call workflow.';
