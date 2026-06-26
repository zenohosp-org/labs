-- V12 — Phase 7a: rename lab_test_catalog → lab_services.
--
-- We discovered (after Phase 3's FK additions) that the "catalog" framing
-- is confusing alongside HMS's hospital_services. The labs-side equivalent
-- of hospital_services is the per-hospital list of measurable analytes —
-- conceptually the labs SERVICE catalog, not a generic test catalog.
--
-- Hibernate's ddl-auto=update cannot rename a table — only a Flyway DDL
-- ALTER does this safely. Postgres' FK constraints follow the table rename
-- automatically (the catalog target moves with the rename), but every
-- *named* index / unique constraint / CHECK constraint that carried the old
-- name needs an explicit RENAME so future DROP / ADD operations stay sane.
--
-- We ALSO rename the child FK column lab_test_id → lab_service_id on
-- lab_reference_ranges, lab_package_items, health_package_tests so the
-- naming matches the new owner. Java entity field renames + repo method
-- renames land in the same PR.

-- ── Table rename ─────────────────────────────────────────────────────────
ALTER TABLE lab_test_catalog RENAME TO lab_services;

-- Indexes carry the old name — rename them so DROP/ADD works later.
ALTER INDEX IF EXISTS idx_lab_test_catalog_hospital_active
    RENAME TO idx_lab_services_hospital_active;
ALTER INDEX IF EXISTS idx_lab_test_catalog_loinc
    RENAME TO idx_lab_services_loinc;
ALTER INDEX IF EXISTS idx_lab_test_catalog_panel
    RENAME TO idx_lab_services_panel;
ALTER INDEX IF EXISTS idx_lab_test_catalog_hospital_service
    RENAME TO idx_lab_services_hospital_service;

-- Unique constraint from V6.
ALTER TABLE lab_services
    RENAME CONSTRAINT uq_lab_test_catalog_hospital_code TO uq_lab_services_hospital_code;

-- V11 CHECK constraints — same physical column, new constraint name.
ALTER TABLE lab_services
    RENAME CONSTRAINT chk_lab_test_catalog_discipline TO chk_lab_services_discipline;
ALTER TABLE lab_services
    RENAME CONSTRAINT chk_lab_test_catalog_value_type TO chk_lab_services_value_type;
ALTER TABLE lab_services
    RENAME CONSTRAINT chk_lab_test_catalog_specimen_kind TO chk_lab_services_specimen_kind;
ALTER TABLE lab_services
    RENAME CONSTRAINT chk_lab_test_catalog_container_type TO chk_lab_services_container_type;

-- ── Child FK column renames ──────────────────────────────────────────────
-- Postgres preserves FK constraints across column rename, so the
-- ON DELETE CASCADE / SET NULL behaviour from V9 still applies.

ALTER TABLE lab_reference_ranges RENAME COLUMN lab_test_id TO lab_service_id;
ALTER INDEX IF EXISTS idx_lab_reference_ranges_lab_test
    RENAME TO idx_lab_reference_ranges_lab_service;

ALTER TABLE lab_package_items RENAME COLUMN lab_test_id TO lab_service_id;
ALTER INDEX IF EXISTS idx_lab_package_items_lab_test
    RENAME TO idx_lab_package_items_lab_service;

ALTER TABLE health_package_tests RENAME COLUMN lab_test_id TO lab_service_id;
ALTER INDEX IF EXISTS idx_health_package_tests_lab_test
    RENAME TO idx_health_package_tests_lab_service;

-- ── Comments updated to match new naming ─────────────────────────────────
COMMENT ON TABLE lab_services IS
    'Per-hospital authoritative list of testable analytes / panels with LOINC codes + workflow defaults. Renamed from lab_test_catalog in V12 to mirror HMS hospital_services naming.';
COMMENT ON COLUMN lab_reference_ranges.lab_service_id IS
    'FK to lab_services(id). Cascade-deleted. NULL on legacy / unmatched rows.';
COMMENT ON COLUMN lab_package_items.lab_service_id IS
    'FK to lab_services(id). SET NULL on delete — package survives test removal via legacy investigation_name.';
COMMENT ON COLUMN health_package_tests.lab_service_id IS
    'FK to lab_services(id). SET NULL on delete — checkup package survives test removal via legacy test_name.';
