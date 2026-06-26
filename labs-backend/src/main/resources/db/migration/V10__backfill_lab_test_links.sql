-- V10 — Phase 3 schema repair + backfill.
--
-- ORIGINAL INTENT was backfill-only — match free-text columns to
-- lab_test_catalog rows and populate the FK columns added by V9.
--
-- DISCOVERED ON FIRST RUN: lab_test_id columns pre-existed as UUID on
-- shared dev DBs (Hibernate auto-ddl from a prior entity definition
-- created them — HMS uses UUID PKs across the board). PostgreSQL's
-- `ADD COLUMN IF NOT EXISTS lab_test_id BIGINT` in V9 was a silent no-op
-- because the column already existed (the IF NOT EXISTS guard ignores
-- type mismatches), and the backfill UPDATE then tried to assign a
-- BIGINT (lab_test_catalog.id) to a UUID column → SQLSTATE 42804.
--
-- This V10 first reshapes the columns to BIGINT (DROP CASCADE removes
-- the existing column + any orphaned indexes / FKs), recreates them
-- with the correct type + FK reference, then runs the backfill in the
-- same transaction.
--
-- Safe to drop the existing columns: they were created by Hibernate
-- but no labs service ever persisted to them (the entity field is new
-- in Phase 3). If you DO see populated UUID values in production,
-- snapshot them before applying — this migration discards them.

-- ── Schema repair: drop + recreate with the correct type ─────────────
ALTER TABLE lab_reference_ranges DROP COLUMN IF EXISTS lab_test_id CASCADE;
ALTER TABLE lab_reference_ranges
    ADD COLUMN lab_test_id BIGINT
    REFERENCES lab_test_catalog(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_lab_reference_ranges_lab_test
    ON lab_reference_ranges (lab_test_id)
    WHERE lab_test_id IS NOT NULL;

ALTER TABLE lab_package_items DROP COLUMN IF EXISTS lab_test_id CASCADE;
ALTER TABLE lab_package_items
    ADD COLUMN lab_test_id BIGINT
    REFERENCES lab_test_catalog(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_lab_package_items_lab_test
    ON lab_package_items (lab_test_id)
    WHERE lab_test_id IS NOT NULL;

ALTER TABLE health_package_tests DROP COLUMN IF EXISTS lab_test_id CASCADE;
ALTER TABLE health_package_tests
    ADD COLUMN lab_test_id BIGINT
    REFERENCES lab_test_catalog(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_health_package_tests_lab_test
    ON health_package_tests (lab_test_id)
    WHERE lab_test_id IS NOT NULL;

-- ── Backfill (best effort, scoped per hospital, case-insensitive) ─────
-- Match priority: lab_test_catalog.name → test_code → aliases (comma list).
-- Each UPDATE is idempotent — re-runnable after the catalogue grows.

UPDATE lab_reference_ranges r
SET lab_test_id = t.id
FROM lab_test_catalog t
WHERE r.lab_test_id IS NULL
  AND r.hospital_id = t.hospital_id
  AND (
        LOWER(r.test_name) = LOWER(t.name)
        OR LOWER(r.test_name) = LOWER(t.test_code)
        OR (t.aliases IS NOT NULL
            AND ',' || REPLACE(LOWER(t.aliases), ' ', '') || ','
                LIKE '%,' || REPLACE(LOWER(r.test_name), ' ', '') || ',%')
      );

UPDATE lab_package_items i
SET lab_test_id = t.id
FROM lab_test_catalog t, lab_packages p
WHERE i.lab_test_id IS NULL
  AND i.package_id = p.id
  AND p.hospital_id = t.hospital_id
  AND (
        LOWER(i.investigation_name) = LOWER(t.name)
        OR LOWER(i.investigation_name) = LOWER(t.test_code)
        OR (t.aliases IS NOT NULL
            AND ',' || REPLACE(LOWER(t.aliases), ' ', '') || ','
                LIKE '%,' || REPLACE(LOWER(i.investigation_name), ' ', '') || ',%')
      );

UPDATE health_package_tests ht
SET lab_test_id = t.id
FROM lab_test_catalog t, health_packages hp
WHERE ht.lab_test_id IS NULL
  AND ht.package_id = hp.id
  AND hp.hospital_id = t.hospital_id
  AND (
        LOWER(ht.test_name) = LOWER(t.name)
        OR LOWER(ht.test_name) = LOWER(t.test_code)
        OR (t.aliases IS NOT NULL
            AND ',' || REPLACE(LOWER(t.aliases), ' ', '') || ','
                LIKE '%,' || REPLACE(LOWER(ht.test_name), ' ', '') || ',%')
      );
