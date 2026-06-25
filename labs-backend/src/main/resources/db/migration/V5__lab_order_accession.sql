-- V5 — Phase 1: lab-wide accession number on lab_orders.
--
-- Today lab_orders.id (BIGSERIAL) is the only identifier and isn't safe to
-- print on barcodes or share with referring labs (leaks order volume). NABL
-- expects a year-prefixed, hospital-prefixed accession number — added here as
-- a nullable column so existing rows stay valid; LabService assigns one on
-- the next mark-collected for in-flight orders, and on creation going forward.
--
-- Format chosen by LabService.generateAccessionNumber():
--   {HOSPITAL_NUMERIC_CODE}-ACC-{YYYY}-{6-digit zero-padded sequence}
-- e.g.  HOSP01-ACC-2026-000123

ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS accession_number VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lab_orders_accession
    ON lab_orders (accession_number)
    WHERE accession_number IS NOT NULL;

COMMENT ON COLUMN lab_orders.accession_number IS
    'Public lab-wide identifier printed on requisitions / specimen barcodes. Year + hospital prefixed.';
