-- V24 — fix reference-range seed/catalog drift found while auditing why
-- typed-in per-analyte results never show a LOW/NORMAL/HIGH flag for
-- several standard analytes.
--
-- Root cause: LabReferenceRangeService matches purely on an exact
-- (case-insensitive) test_name string (see findCandidates query) against
-- whatever LabCatalogService resolves the analyte's display name to. Two
-- independent seed classes (LabServiceSeed for the catalogue, and
-- LabReferenceRangeSeed for the bands) drifted apart on naming, and three
-- LFT/Lipid/Thyroid analytes were never given a default band at all:
--
--   catalog name (LabServiceSeed)   ref-range seed name (LabReferenceRangeSeed)
--   "Hematocrit"                 vs "PCV / Hematocrit"            -- mismatch
--   "Post-Prandial Glucose"      vs "Post-Prandial Blood Sugar"   -- mismatch
--   "Total Bilirubin"            vs "Bilirubin Total"             -- word order
--   "Direct Bilirubin"           vs "Bilirubin Direct"            -- word order
--   "Gamma-GT" / "Total Protein" / "Albumin"       -- no seed row at all
--   "VLDL Cholesterol"                             -- no seed row at all
--   "Total T3" / "Total T4"                        -- no seed row at all
--
-- LabReferenceRangeSeed.java is fixed alongside this migration so any NEW
-- hospital seeds correctly from day one — but the seeder only runs once per
-- hospital (lazy-seeded on first read, gated on countByHospitalId == 0), so
-- every hospital that already seeded before this fix would otherwise stay
-- broken forever. This migration repairs those hospitals directly:
--   1. rename the four mismatched rows via UPDATE so they match the
--      catalogue's actual names;
--   2. backfill the six previously-unseeded analytes per hospital, guarded by
--      NOT EXISTS so it's safe to run against a hospital at any point in its
--      history (including one that manually added a correctly-named row
--      already) and idempotent if ever re-run.
--
-- Values are the same standard adult Indian-lab reference ranges the rest of
-- LabReferenceRangeSeed already draws from (Henry's Clinical Diagnosis / NIN)
-- — conservative, editable per-hospital, no global authority.

-- ── 1. Rename mismatched rows so they match the catalogue's actual names ──
UPDATE lab_reference_ranges SET test_name = 'Hematocrit', updated_at = now()
    WHERE test_name = 'PCV / Hematocrit';

UPDATE lab_reference_ranges SET test_name = 'Post-Prandial Glucose', updated_at = now()
    WHERE test_name = 'Post-Prandial Blood Sugar';

UPDATE lab_reference_ranges SET test_name = 'Total Bilirubin', updated_at = now()
    WHERE test_name = 'Bilirubin Total';

UPDATE lab_reference_ranges SET test_name = 'Direct Bilirubin', updated_at = now()
    WHERE test_name = 'Bilirubin Direct';

-- ── 2. Backfill the six analytes that never had a default band ────────────
-- One INSERT per analyte, fanned out to every hospital that already has at
-- least one reference range (i.e. has been lazy-seeded) and doesn't already
-- have a row for that test name.

INSERT INTO lab_reference_ranges
    (id, hospital_id, test_name, category, sex, min_age_years, max_age_years,
     min_value, max_value, unit, range_text, is_active, created_at, updated_at)
SELECT gen_random_uuid(), h.hospital_id, 'Gamma-GT', 'BIOCHEMISTRY', 'ANY', 0, 200,
       0, 55, 'U/L', '0 – 55 U/L', true, now(), now()
FROM (SELECT DISTINCT hospital_id FROM lab_reference_ranges) h
WHERE NOT EXISTS (
    SELECT 1 FROM lab_reference_ranges r
    WHERE r.hospital_id = h.hospital_id AND LOWER(r.test_name) = LOWER('Gamma-GT')
);

INSERT INTO lab_reference_ranges
    (id, hospital_id, test_name, category, sex, min_age_years, max_age_years,
     min_value, max_value, unit, range_text, is_active, created_at, updated_at)
SELECT gen_random_uuid(), h.hospital_id, 'Total Protein', 'BIOCHEMISTRY', 'ANY', 0, 200,
       6.0, 8.3, 'g/dL', '6.0 – 8.3 g/dL', true, now(), now()
FROM (SELECT DISTINCT hospital_id FROM lab_reference_ranges) h
WHERE NOT EXISTS (
    SELECT 1 FROM lab_reference_ranges r
    WHERE r.hospital_id = h.hospital_id AND LOWER(r.test_name) = LOWER('Total Protein')
);

INSERT INTO lab_reference_ranges
    (id, hospital_id, test_name, category, sex, min_age_years, max_age_years,
     min_value, max_value, unit, range_text, is_active, created_at, updated_at)
SELECT gen_random_uuid(), h.hospital_id, 'Albumin', 'BIOCHEMISTRY', 'ANY', 0, 200,
       3.5, 5.0, 'g/dL', '3.5 – 5.0 g/dL', true, now(), now()
FROM (SELECT DISTINCT hospital_id FROM lab_reference_ranges) h
WHERE NOT EXISTS (
    SELECT 1 FROM lab_reference_ranges r
    WHERE r.hospital_id = h.hospital_id AND LOWER(r.test_name) = LOWER('Albumin')
);

INSERT INTO lab_reference_ranges
    (id, hospital_id, test_name, category, sex, min_age_years, max_age_years,
     min_value, max_value, unit, range_text, is_active, created_at, updated_at)
SELECT gen_random_uuid(), h.hospital_id, 'VLDL Cholesterol', 'BIOCHEMISTRY', 'ANY', 0, 200,
       2, 30, 'mg/dL', '2 – 30 mg/dL', true, now(), now()
FROM (SELECT DISTINCT hospital_id FROM lab_reference_ranges) h
WHERE NOT EXISTS (
    SELECT 1 FROM lab_reference_ranges r
    WHERE r.hospital_id = h.hospital_id AND LOWER(r.test_name) = LOWER('VLDL Cholesterol')
);

INSERT INTO lab_reference_ranges
    (id, hospital_id, test_name, category, sex, min_age_years, max_age_years,
     min_value, max_value, unit, range_text, is_active, created_at, updated_at)
SELECT gen_random_uuid(), h.hospital_id, 'Total T3', 'ENDOCRINOLOGY', 'ANY', 18, 200,
       80, 200, 'ng/dL', '80 – 200 ng/dL', true, now(), now()
FROM (SELECT DISTINCT hospital_id FROM lab_reference_ranges) h
WHERE NOT EXISTS (
    SELECT 1 FROM lab_reference_ranges r
    WHERE r.hospital_id = h.hospital_id AND LOWER(r.test_name) = LOWER('Total T3')
);

INSERT INTO lab_reference_ranges
    (id, hospital_id, test_name, category, sex, min_age_years, max_age_years,
     min_value, max_value, unit, range_text, is_active, created_at, updated_at)
SELECT gen_random_uuid(), h.hospital_id, 'Total T4', 'ENDOCRINOLOGY', 'ANY', 18, 200,
       5.0, 12.0, 'µg/dL', '5.0 – 12.0 µg/dL', true, now(), now()
FROM (SELECT DISTINCT hospital_id FROM lab_reference_ranges) h
WHERE NOT EXISTS (
    SELECT 1 FROM lab_reference_ranges r
    WHERE r.hospital_id = h.hospital_id AND LOWER(r.test_name) = LOWER('Total T4')
);
