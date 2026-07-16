-- ═══════════════════════════════════════════════════════════════════════════
--  Activate dormant lab_services rows — per tenant or fleet-wide.
--
--  Companion to the LOINC seeds, which land the catalogue switched off. This
--  turns rows on without clicking 1834 toggles in Settings → Lab Services.
--
--  Only ever flips active false → true. Never deactivates, never touches the
--  app's curated baseline (already active). Idempotent.
--
--  ⚠️  Before switching on the whole catalogue, make sure the build carrying
--      server.compression.enabled=true is DEPLOYED. GET /api/lab-services is
--      unpaginated and PerAnalyteResultEntry pulls the full active set on
--      every open: 1527 KB raw vs 123 KB gzipped (measured, 1901 rows).
--
--  ── USAGE ────────────────────────────────────────────────────────────────
--    # everything, every live tenant
--    psql "$DB_URL" -v categories=ALL -f .../activate_lab_services.sql
--
--    # everything, one tenant
--    psql "$DB_URL" -v hid=<uuid> -v categories=ALL -f .../activate_lab_services.sql
--
--    # specific LOINC classes, fleet-wide
--    psql "$DB_URL" -v categories='CHEM,HEM/BC,COAG' -f .../activate_lab_services.sql
--
--    # individual tests by LOINC code
--    psql "$DB_URL" -v loincs='718-7,4544-3,2160-0' -f .../activate_lab_services.sql
--
--  Omit -v hid to hit every tenant that has a catalogue (including tenants
--  present in lab_services but absent from hospitals — e.g. dev/orphan ones).
--
--  ── Deactivating again ───────────────────────────────────────────────────
--    Reversible. To roll the LOINC seed back to dormant for one tenant:
--      UPDATE lab_services SET active = false, updated_at = now()
--       WHERE hospital_id = '<uuid>' AND loinc_code IS NOT NULL
--         AND test_code NOT IN (SELECT test_code FROM lab_services
--                                WHERE hospital_id = '<uuid>' AND price IS NOT NULL);
--    (the curated baseline is the priced set — never blanket-deactivate it)
-- ═══════════════════════════════════════════════════════════════════════════

\set ON_ERROR_STOP on
\if :{?hid}        \else \set hid        '' \endif
\if :{?categories} \else \set categories '' \endif
\if :{?loincs}     \else \set loincs     '' \endif

BEGIN;

CREATE TEMP TABLE _act_ctx ON COMMIT DROP AS
    SELECT nullif(:'hid', '')::uuid                              AS hospital_id,   -- NULL = fleet-wide
           upper(nullif(:'categories', '')) = 'ALL'              AS all_rows,
           CASE WHEN upper(nullif(:'categories','')) = 'ALL' THEN NULL
                ELSE string_to_array(nullif(:'categories',''), ',') END AS categories,
           string_to_array(nullif(:'loincs', ''), ',')           AS loincs;

DO $guard$
DECLARE h uuid; allr boolean; cats text[]; lns text[]; n bigint;
BEGIN
    SELECT hospital_id, all_rows, categories, loincs INTO h, allr, cats, lns FROM _act_ctx;

    IF NOT allr AND cats IS NULL AND lns IS NULL THEN
        RAISE EXCEPTION 'nothing to do: pass -v categories=ALL, -v categories=<csv> and/or -v loincs=<csv>';
    END IF;

    -- Guard on lab_services, not hospitals: a tenant can legitimately have a
    -- catalogue without a hospitals row (dev/orphan tenants), and activation
    -- only ever touches rows that already exist.
    IF h IS NOT NULL THEN
        SELECT count(*) INTO n FROM lab_services WHERE hospital_id = h;
        IF n = 0 THEN
            RAISE EXCEPTION 'hospital_id % has no lab_services rows — nothing to activate', h;
        END IF;
        RAISE NOTICE 'scope=single tenant %  (% rows)', h, n;
    ELSE
        SELECT count(DISTINCT hospital_id) INTO n FROM lab_services;
        RAISE NOTICE 'scope=fleet-wide (% tenants)', n;
    END IF;
END
$guard$;

UPDATE lab_services s
   SET active = true,
       updated_at = now()          -- mirrors the entity's @PreUpdate
  FROM _act_ctx c
 WHERE (c.hospital_id IS NULL OR s.hospital_id = c.hospital_id)
   AND s.active = false
   AND (
         c.all_rows
      OR (c.categories IS NOT NULL AND s.category   = ANY (c.categories))
      OR (c.loincs     IS NOT NULL AND s.loinc_code = ANY (c.loincs))
       );

COMMIT;

\echo ''
\echo 'Catalogue per tenant after activation:'
SELECT COALESCE(h.name, '(orphan/dev tenant)')     AS hospital,
       count(*)                                    AS total_rows,
       count(*) FILTER (WHERE s.active)            AS active_rows,
       count(*) FILTER (WHERE NOT s.active)        AS dormant_rows
FROM lab_services s
LEFT JOIN hospitals h ON h.id = s.hospital_id
GROUP BY 1
ORDER BY 1;
