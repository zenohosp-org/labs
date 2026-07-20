-- V22 — remove the dormant per-hospital LOINC rows left by the old bulk seed.
--
-- Before the master-catalog model (V20/V21), the LOINC universe was bulk-seeded
-- into EVERY hospital's lab_services as ~1,834 dormant rows. That content now
-- lives once in lab_service_catalog, and a hospital's lab_services should hold
-- only the tests it actually offers. This deletes that dormant clutter so the
-- admin page shows a hospital's real offered list.
--
-- Runs once per database on deploy. On a fresh DB (no old seed) it deletes 0.
--
-- ── Predicate, and why each condition is required ────────────────────────────
--   loinc_code IS NOT NULL        LOINC provenance
--   price      IS NULL            spares curated PANELS + RADIOLOGY (both priced)
--   parent_panel_code IS NULL     spares curated child ANALYTES (CBC/LFT/... —
--                                 they carry a loinc_code and no price)
--   active     = false            spares tests an admin ADDS via the new "Add
--                                 from catalog" picker: those insert active=true
--                                 and, with price left blank, would otherwise
--                                 match the first three conditions exactly and
--                                 be wrongly deleted. The dormant seed is the
--                                 only active=false LOINC set. (Verified in
--                                 review — without this guard a real offered
--                                 test could vanish and cascade its ranges.)
--
-- ── Boot-safe by construction ────────────────────────────────────────────────
-- lab_reference_ranges is ON DELETE CASCADE, so a wrong delete would silently
-- drop reference bands. Rather than a guard that RAISEs (which would abort the
-- app's boot), every FK child is excluded with NOT EXISTS: a row is deleted
-- only if it has zero dependents. Anything referenced is simply left in place.
-- The dormant seed rows were never orderable, so all 1,834/tenant qualify;
-- if any didn't, it would be spared, not cascade-deleted.

DELETE FROM lab_services s
WHERE s.loinc_code        IS NOT NULL
  AND s.price             IS NULL
  AND s.parent_panel_code IS NULL
  AND s.active            = false
  AND NOT EXISTS (SELECT 1 FROM lab_orders           o WHERE o.lab_service_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM radiology_orders     r WHERE r.lab_service_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM lab_reference_ranges x WHERE x.lab_service_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM lab_package_items    p WHERE p.lab_service_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM health_package_tests h WHERE h.lab_service_id = s.id);
