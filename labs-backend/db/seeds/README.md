# labs-backend/db/seeds

Per-tenant **data** seeds. Run by hand.

## Why these are not Flyway migrations

`src/main/resources/db/migration` is Flyway's classpath location and runs
automatically at boot, **once per database**. These seeds are the wrong shape
for that:

- The rows belong to a **`hospital_id`**, and the labs DB is multi-tenant and
  shared with HMS. A migration would have to hardcode a UUID.
- A migration runs once, so **tenants onboarded later would never get the
  catalogue**.
- `spring.flyway.validate-on-migrate=true` checksum-locks whatever is applied.

So these live outside `src/main/resources` — off the classpath, never bundled
into the jar, impossible to auto-apply. **Do not move them into `db/migration`.**

## The baseline gate (read this before seeding a new tenant)

`LabCatalogService.list()` lazy-seeds ~47 curated rows (CBC/LFT/RFT/LIPID/
THYROID/DIAB/URINE panels + child analytes + prices) on the **first**
`GET /api/lab-services`, gated on `countByHospitalId(hospitalId) == 0`.

Seeding LOINC into an empty catalogue trips that gate **permanently**: the
curated panels and their `parent_panel_code` wiring would never be created, and
the LOINC set has **zero `parent_panel_code` rows** — so panel expansion in
per-analyte result entry would silently break.

Both seed scripts therefore treat "has rows" as the eligibility test. A tenant
with 0 rows is **skipped, not seeded**. To bring one online: open
Settings → Lab Services once for it (or `GET /api/lab-services`), confirm the
baseline landed, then re-run the fleet seed — it's idempotent.

## Files

| file | what |
|---|---|
| `seed_lab_services_loinc_fleet.sql` | 1834 LOINC 2.82 rows → **every live tenant**. The normal one to run. |
| `seed_lab_services_loinc.sql` | Same rows, **one tenant** (`-v hid=<uuid>`). Targeted / debugging. |
| `activate_lab_services.sql` | Flip dormant rows → active, per tenant or fleet-wide. |

All are DDL-free, transactional, idempotent, and abort on a bad target.

## Runbook

**1. Seed (dormant) — safe, reversible, no user-visible change.**
```sh
psql "$DB_URL" -v mode=dormant \
     -f labs-backend/db/seeds/seed_lab_services_loinc_fleet.sql
```
Both hot paths (`GET /api/lab-services?activeOnly=true` → PerAnalyteResultEntry,
and `/search` → TestPicker) filter `active = true`, so dormant rows are inert.
`mode=as_ranked` instead honours LOINC's own ranking (1227 active/tenant).

**2. Deploy `server.compression.enabled=true` before switching the catalogue on.**
`GET /api/lab-services` is unpaginated and nothing sits in front of the jar
(no nginx). Measured on real data at 1901 rows: **1527 KB raw → 123 KB gzipped
(12.3x)**. Without compression, that full payload hits a lab tech's screen on
every result-entry open.

**3. Activate.**
```sh
# everything, every tenant
psql "$DB_URL" -v categories=ALL -f labs-backend/db/seeds/activate_lab_services.sql

# or just the bread-and-butter classes
psql "$DB_URL" -v categories='CHEM,HEM/BC,COAG,UA' -f .../activate_lab_services.sql
```
Then fine-tune per test in Settings → Lab Services — the toggle / edit / add UI
already covers every column.

## Note on `active` after the first run

The seeds use `ON CONFLICT (hospital_id, test_code) DO NOTHING`, so re-running
with a different `mode` **changes nothing** — existing rows are skipped. Change
activation with `activate_lab_services.sql` or the UI, not by re-seeding.

## Known wrinkles

- **Category taxonomy is mixed.** The curated baseline uses
  `HAEMATOLOGY`/`BIOCHEMISTRY`/`ENDOCRINOLOGY`; the LOINC seed brings 31 LOINC
  classes (`CHEM`, `HEM/BC`, `MICRO`, `UA`, …). The settings page derives its
  filter chips via `new Set(rows.map(r => r.category))`, so both taxonomies show
  side by side. Cosmetic — decide on a mapping if it bothers you.
- **Semantic duplicates.** Curated `HB` (Hemoglobin) and LOINC
  `Hgb Bld-mCnc` are both LOINC `718-7`. Different `test_code`, both valid; the
  curated one is the priced/orderable row.
- **LOINC panels are flat.** 53 rows have `is_panel = true` but no children
  (`parent_panel_code` is NULL across the whole seed), so `GET
  /api/lab-services/panel/{code}` returns empty for them. Only the curated 7
  panels expand.
