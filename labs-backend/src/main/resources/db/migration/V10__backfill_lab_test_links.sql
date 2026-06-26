-- V10 — Phase 3 backfill.
--
-- Best-effort backfill of the FK columns added in V9 by matching the
-- existing free-text columns to lab_test_catalog rows in the SAME hospital.
-- Every UPDATE is idempotent — it only touches rows where lab_test_id is
-- still NULL, so re-running this migration after the catalogue grows just
-- picks up the new matches without disturbing existing links.
--
-- Match priority (per hospital):
--   1. exact case-insensitive match on lab_test_catalog.name
--   2. exact case-insensitive match on lab_test_catalog.test_code
--   3. comma-separated match against lab_test_catalog.aliases
--
-- Rows that still don't match after this stay with lab_test_id NULL and
-- behave exactly like today — the free-text column is the lookup key.

-- ── lab_reference_ranges ──────────────────────────────────────────────────
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

-- ── lab_package_items ─────────────────────────────────────────────────────
-- A lab_package_item belongs to a lab_package which belongs to a hospital.
-- Join through lab_packages to scope the catalogue match to the same
-- hospital_id (catalogue is per-hospital).
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

-- ── health_package_tests ──────────────────────────────────────────────────
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
