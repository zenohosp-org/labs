-- V9 — Phase 3 (catalogue integration).
--
-- Adds real FK links from every place that referenced a test by free text
-- ("Hemoglobin" string column) to the canonical lab_test_catalog row, plus
-- an optional pointer from a catalogue row back to its HMS billing service.
--
-- After this lands:
--   * one Hemoglobin row in lab_test_catalog is the single source of truth
--     for LOINC, unit, container, panel membership, signoff rule, price;
--   * lab_reference_ranges (the bands) is OWNED by that test row — cascade-
--     deleted with it;
--   * lab_package_items and health_package_tests reference the test by id
--     instead of free text — survive a test deletion via SET NULL with the
--     legacy text column as fallback;
--   * lab_test_catalog can point at an HMS hospital_services row when the
--     test is itself a billable service (loose link — we don't own that
--     table, no FK constraint).
--
-- Backward compat: every existing column is preserved (test_name on ranges,
-- investigation_name on package items, test_name on health package tests).
-- Old API requests that omit lab_test_id continue to work — V10 backfills
-- the FK where the free-text name matches; rows that can't be matched stay
-- with lab_test_id NULL and behave like today.

-- ── lab_test_catalog ──────────────────────────────────────────────────────
-- Optional pointer to the HMS hospital_services row that represents this
-- test on the billing side. Nullable; no FK because hospital_services is
-- HMS-owned and the FK constraint would couple deploys.
ALTER TABLE lab_test_catalog
    ADD COLUMN IF NOT EXISTS hospital_service_id UUID;

CREATE INDEX IF NOT EXISTS idx_lab_test_catalog_hospital_service
    ON lab_test_catalog (hospital_service_id)
    WHERE hospital_service_id IS NOT NULL;

COMMENT ON COLUMN lab_test_catalog.hospital_service_id IS
    'Loose link to hms.hospital_services.id — set when this test is also a billable service. No FK (HMS owns the table).';

-- ── lab_reference_ranges ──────────────────────────────────────────────────
-- The bands are OWNED by the catalogue row. Cascade delete: deleting a test
-- removes its ranges (the original test_name string stays for legacy UI).
ALTER TABLE lab_reference_ranges
    ADD COLUMN IF NOT EXISTS lab_test_id BIGINT
        REFERENCES lab_test_catalog(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_lab_reference_ranges_lab_test
    ON lab_reference_ranges (lab_test_id)
    WHERE lab_test_id IS NOT NULL;

COMMENT ON COLUMN lab_reference_ranges.lab_test_id IS
    'FK to lab_test_catalog. Cascade-deleted. NULL on legacy / unmatched rows; test_name remains the lookup key in that case.';

-- ── lab_package_items ─────────────────────────────────────────────────────
-- A package item points at the canonical test. SET NULL on test delete so
-- the package survives — investigation_name (free text) is the fallback.
ALTER TABLE lab_package_items
    ADD COLUMN IF NOT EXISTS lab_test_id BIGINT
        REFERENCES lab_test_catalog(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_lab_package_items_lab_test
    ON lab_package_items (lab_test_id)
    WHERE lab_test_id IS NOT NULL;

COMMENT ON COLUMN lab_package_items.lab_test_id IS
    'FK to lab_test_catalog. NULL when the package was authored from free text or after a linked test was deleted.';

-- ── health_package_tests ──────────────────────────────────────────────────
-- Same shape as lab_package_items. SET NULL preserves the package on delete.
ALTER TABLE health_package_tests
    ADD COLUMN IF NOT EXISTS lab_test_id BIGINT
        REFERENCES lab_test_catalog(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_health_package_tests_lab_test
    ON health_package_tests (lab_test_id)
    WHERE lab_test_id IS NOT NULL;

COMMENT ON COLUMN health_package_tests.lab_test_id IS
    'FK to lab_test_catalog. NULL when authored from free text or after a linked test was deleted.';
