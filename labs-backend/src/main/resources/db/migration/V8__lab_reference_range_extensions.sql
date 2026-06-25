-- V8 — Phase 2: extend lab_reference_ranges with NABL-grade metadata.
--
-- Today a reference range is (test_name, sex, age window, min/max, unit,
-- range_text). That's enough for "is this WBC count low?" but misses:
--   * critical limits — values that cross these are PANIC, not just LOW/HIGH;
--   * special physiological states — pregnancy + neonate + fasting bands;
--   * LOINC linkage — so a result tagged with LOINC code can find its range
--     even when the free-text name doesn't match;
--   * version history — NABL audits demand we can show which range was in
--     effect when a historical result was flagged.
--
-- All additions are nullable + indexed-when-set so existing ranges keep
-- matching exactly as before. The match algorithm in
-- LabReferenceRangeService is updated in this same phase to use the new
-- columns when present.

ALTER TABLE lab_reference_ranges
    ADD COLUMN IF NOT EXISTS critical_low    DECIMAL(12,4),
    ADD COLUMN IF NOT EXISTS critical_high   DECIMAL(12,4),
    ADD COLUMN IF NOT EXISTS special_state   VARCHAR(30),                    -- PREGNANT | NEONATE | FASTING | POSTPRANDIAL | NULL=baseline
    ADD COLUMN IF NOT EXISTS loinc_code      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS method          VARCHAR(80),
    ADD COLUMN IF NOT EXISTS effective_from  DATE,
    ADD COLUMN IF NOT EXISTS effective_to    DATE,                           -- NULL = currently active
    ADD COLUMN IF NOT EXISTS source_citation VARCHAR(300);                   -- e.g. "Tietz 6th ed, Indian Pediatric Endocrine Society 2024"

CREATE INDEX IF NOT EXISTS idx_lab_ref_ranges_loinc
    ON lab_reference_ranges (loinc_code) WHERE loinc_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lab_ref_ranges_special_state
    ON lab_reference_ranges (hospital_id, test_name, special_state)
    WHERE special_state IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lab_ref_ranges_effective
    ON lab_reference_ranges (hospital_id, test_name, effective_to)
    WHERE effective_to IS NULL;

COMMENT ON COLUMN lab_reference_ranges.critical_low IS
    'Values strictly below this are panic-low (HL7 LL). Triggers panic-call workflow on the result.';
COMMENT ON COLUMN lab_reference_ranges.critical_high IS
    'Values strictly above this are panic-high (HL7 HH). Triggers panic-call workflow on the result.';
COMMENT ON COLUMN lab_reference_ranges.special_state IS
    'PREGNANT | NEONATE | FASTING | POSTPRANDIAL — NULL is the baseline band. Most specific match wins.';
COMMENT ON COLUMN lab_reference_ranges.effective_to IS
    'NULL = currently in force. A non-null date means this range was superseded; kept for historical result audit.';
