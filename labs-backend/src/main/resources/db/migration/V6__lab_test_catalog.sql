-- V6 — Phase 2: per-hospital LOINC-coded test catalogue.
--
-- Today every test is a free-text `service_name` on lab_orders. That's fine
-- for billing but useless for analytical work — you can't deduplicate "CBC",
-- "Complete Blood Count", and "C.B.C." across orders, you can't ship FHIR
-- DiagnosticReport / Observation without LOINC codes, and you can't auto-
-- flag results without an authoritative test → reference-range link.
--
-- This catalogue is OPT-IN: existing lab_orders keep their free-text
-- service_name and continue to function. New orders may set
-- primary_test_code so per-analyte results in V7 can be classified properly.
-- Hospitals start with an empty catalogue and either seed from V9 or build
-- their own via the admin UI.

CREATE TABLE IF NOT EXISTS lab_test_catalog (
    id                       BIGSERIAL    PRIMARY KEY,
    hospital_id              UUID         NOT NULL,

    -- Identity
    test_code                VARCHAR(50)  NOT NULL,    -- in-house short code, e.g. "HB", "CBC", "TSH"
    loinc_code               VARCHAR(20),              -- LOINC e.g. "718-7" for hemoglobin
    name                     VARCHAR(200) NOT NULL,    -- human-readable, e.g. "Hemoglobin"
    aliases                  VARCHAR(500),             -- comma-separated alt names for search

    category                 VARCHAR(50),              -- HAEMATOLOGY | BIOCHEMISTRY | ENDOCRINOLOGY | MICROBIOLOGY | etc.
    discipline               VARCHAR(30),              -- PATHOLOGY | RADIOLOGY | CYTOLOGY | HISTOPATHOLOGY (drives signoff rule)
    specimen_kind            VARCHAR(30),              -- BLOOD | URINE | STOOL | SWAB | CSF | TISSUE | OTHER

    -- Pre-analytical defaults — pre-fill the specimen modal
    default_container_type   VARCHAR(50),              -- EDTA | CITRATE | HEPARIN | PLAIN | FLUORIDE | URINE_CUP | STOOL_CUP | SWAB | OTHER
    default_additive         VARCHAR(50),
    default_volume_ml        DECIMAL(6,2),
    fasting_required         BOOLEAN      NOT NULL DEFAULT FALSE,
    stability_minutes        INTEGER,                  -- analyte stability after collection; UI warns if exceeded

    -- Analytical defaults
    default_method           VARCHAR(80),              -- e.g. "Cyanide-free SLS", "Immunoassay", "Spectrophotometry"
    default_unit             VARCHAR(50),              -- UCUM unit string, e.g. "g/dL", "mmol/L"
    value_type               VARCHAR(20)  NOT NULL DEFAULT 'NUMERIC',  -- NUMERIC | TEXT | CODED | RATIO

    -- Workflow rules
    requires_authorisation   BOOLEAN      NOT NULL DEFAULT FALSE,  -- TRUE = pathologist sign-off required after tech verify
    tat_minutes              INTEGER,                              -- target turnaround time
    is_panel                 BOOLEAN      NOT NULL DEFAULT FALSE,  -- TRUE = a composite that expands to child analytes
    parent_panel_code        VARCHAR(50),                          -- for child analytes: links them to the panel they belong to

    -- Commercial defaults (orders may override per-order)
    price                    DECIMAL(10,2),
    gst_rate                 DECIMAL(5,2),

    display_order            INTEGER,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP,

    CONSTRAINT uq_lab_test_catalog_hospital_code UNIQUE (hospital_id, test_code)
);

CREATE INDEX IF NOT EXISTS idx_lab_test_catalog_hospital_active
    ON lab_test_catalog (hospital_id, active, category);

CREATE INDEX IF NOT EXISTS idx_lab_test_catalog_loinc
    ON lab_test_catalog (loinc_code) WHERE loinc_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lab_test_catalog_panel
    ON lab_test_catalog (hospital_id, parent_panel_code) WHERE parent_panel_code IS NOT NULL;

COMMENT ON TABLE lab_test_catalog IS
    'Per-hospital authoritative list of testable analytes / panels with LOINC codes + workflow defaults.';
COMMENT ON COLUMN lab_test_catalog.discipline IS
    'Drives the signoff rule. PATHOLOGY/biochem usually tech-only; HISTOPATHOLOGY/CYTOLOGY require pathologist authorise.';
COMMENT ON COLUMN lab_test_catalog.is_panel IS
    'A panel (e.g. CBC) expands to N child analytes — each child rows in this same table with parent_panel_code set.';
COMMENT ON COLUMN lab_test_catalog.value_type IS
    'NUMERIC = value_numeric. TEXT = free text (cultures, microscopy). CODED = controlled-vocab (urine colour, etc.). RATIO = "1:160" style.';
