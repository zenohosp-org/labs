-- V20 — Phase 11: global LOINC master catalog (lab_service_catalog).
--
-- Until now lab_services was BOTH the master list of possible tests AND each
-- hospital's offered list — so seeding the LOINC universe meant copying tens of
-- thousands of rows into every tenant, and the admin page had to render all of
-- them. That does not scale: the full active LOINC lab set is ~60k terms, and a
-- hospital only performs a few dozen.
--
-- This splits the two concerns, mirroring the proven pharmacy pattern
-- (pharmacy_medicine_catalog: one global, hospital-agnostic, ~222k-row table
-- with a trigram index that every hospital searches):
--
--   lab_service_catalog  — this table. ONE shared copy of the LOINC universe,
--                          no hospital_id. Read-only reference data. Admins
--                          SEARCH it; they never see all of it at once.
--   lab_services         — unchanged. Stays each hospital's small offered list;
--                          rows are created by copying a chosen catalog term
--                          (via the existing POST /api/lab-services upsert).
--
-- Schema only — the ~60k rows are loaded automatically by the Java migration
-- V21__load_lab_service_catalog, which streams a 3.7 MB gzipped CSV resource in
-- via PostgreSQL COPY. They are NOT inlined here: as raw INSERTs that would be a
-- ~24 MB migration Flyway checksums on every boot. The table is global reference
-- data, so it legitimately lives in Flyway; the bulk data just rides in V21.
--
-- Idempotent: IF NOT EXISTS throughout so a re-run is a no-op.

CREATE TABLE IF NOT EXISTS lab_service_catalog (
    id              BIGSERIAL    PRIMARY KEY,

    -- Identity. loinc_code is the natural key of the LOINC universe: globally
    -- unique, stable across releases, and what makes a catalog row addressable.
    loinc_code      VARCHAR(20)  NOT NULL,
    test_code       VARCHAR(50),              -- suggested in-house short code (LOINC_NUM by default)
    name            VARCHAR(200) NOT NULL,    -- LONG_COMMON_NAME
    aliases         VARCHAR(500),             -- LOINC RELATEDNAMES2, for fuzzy search

    -- Analytical metadata copied onto the hospital's row when a term is added.
    -- Same closed vocabularies as lab_services so a copied row already satisfies
    -- lab_services' CHECK constraints — no re-validation, no surprise rejects.
    category        VARCHAR(50),              -- LOINC CLASS (CHEM, HEM/BC, MICRO, …)
    discipline      VARCHAR(30),              -- PATHOLOGY | CYTOLOGY | HISTOPATHOLOGY
    specimen_kind   VARCHAR(30),              -- BLOOD | URINE | STOOL | SWAB | CSF | TISSUE | OTHER
    default_method  VARCHAR(80),
    default_unit    VARCHAR(50),
    value_type      VARCHAR(20)  NOT NULL DEFAULT 'NUMERIC',  -- NUMERIC | TEXT | CODED | RATIO
    is_panel        BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_lab_service_catalog_loinc UNIQUE (loinc_code),
    CONSTRAINT chk_lab_service_catalog_discipline
        CHECK (discipline IS NULL OR discipline IN ('PATHOLOGY','RADIOLOGY','CYTOLOGY','HISTOPATHOLOGY')),
    CONSTRAINT chk_lab_service_catalog_value_type
        CHECK (value_type IN ('NUMERIC','TEXT','CODED','RATIO')),
    CONSTRAINT chk_lab_service_catalog_specimen_kind
        CHECK (specimen_kind IS NULL OR specimen_kind IN ('BLOOD','URINE','STOOL','SWAB','CSF','TISSUE','OTHER'))
);

-- Cheap btree index for category faceting. The expensive GIN TRIGRAM indexes
-- (for the ILIKE typeahead) are deliberately created LATER, in V22, AFTER V21
-- bulk-loads the ~60k rows: building a GIN index once over a full table is
-- fast, whereas maintaining it incrementally during the COPY adds ~75s to boot.
CREATE INDEX IF NOT EXISTS idx_lab_service_catalog_category
    ON lab_service_catalog (category);

COMMENT ON TABLE lab_service_catalog IS
    'Global, hospital-agnostic LOINC master catalog. Admins search it to add tests to their hospital''s lab_services list. Bulk data loaded automatically by Flyway V21 from a gzipped resource. Mirrors pharmacy_medicine_catalog.';
