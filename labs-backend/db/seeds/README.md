# labs-backend/db/seeds

Tooling for the LOINC lab-service catalog. The catalog itself is now loaded
**automatically by Flyway** — there is no hand-run seed step anymore.

## The model (Phase 11)

A global master catalog + a small per-hospital offered list, mirroring
pharmacy's `pharmacy_medicine_catalog`:

| table | scope | filled by |
|---|---|---|
| `lab_service_catalog` | global, no `hospital_id`, ~60k LOINC terms | Flyway **V20** (table) + **V21** (data) |
| `lab_services` | per-hospital, the tests a hospital offers | app baseline + admin adds via the "Add from catalog" picker |

Admins **search** the master (`GET /api/lab-services/catalog`) and pick a term;
the pick seeds the editor form and is created as a hospital-scoped row through
the normal upsert. A hospital never carries the whole 60k — only its offered
tests.

## Flyway migrations (automatic on deploy)

- **V20** `lab_service_catalog_master.sql` — creates the global table + pg_trgm
  GIN indexes for typeahead search.
- **V21** `V21__load_lab_service_catalog.java` — a Java migration that streams
  the gzipped CSV resource
  (`src/main/resources/db/data/lab_service_catalog.csv.gz`, 3.7 MB) into the
  table via PostgreSQL `COPY`. Runs once, ~1s. Skips if already populated.
- **V22** `prune_per_hospital_loinc.sql` — one-time cleanup: removes the ~1,834
  dormant LOINC rows the OLD per-hospital bulk seed left in each hospital's
  `lab_services`. Boot-safe (deletes only dormant, unreferenced rows; spares the
  curated baseline and any admin-added active test).

Deploying the backend runs all three in order — no manual DB step.

## `gen_lab_service_catalog.py` — regenerating the data resource

The raw LOINC table (`Loinc.csv`, ~109k rows, licensed) is not committed. This
generator is the committed provenance for the V21 resource. Regenerate it after
a LOINC release bump:

```sh
python3 gen_lab_service_catalog.py \
    ~/Downloads/loinc/Loinc_2.82/LoincTable/Loinc.csv \
    ../src/main/resources/db/data/lab_service_catalog.csv.gz
```

It selects ACTIVE + Laboratory-class terms (60,009 in 2.82) and maps LOINC
fields into lab_services' closed vocabularies (SCALE_TYP→value_type,
SYSTEM→specimen_kind, CLASS→category/discipline), so a copied row already
satisfies the `lab_services` CHECK constraints. Commit the regenerated `.gz`.

## Why the catalog is in Flyway but per-hospital data is not

The master catalog is **global reference data** — identical for every tenant, so
"run once per database" is exactly right, and it belongs in Flyway. Per-hospital
data (a hospital's offered tests, keyed on `hospital_id`) does NOT: a migration
runs once and later-onboarded tenants would miss it. That is why the old
per-hospital LOINC bulk seed lived here as a hand-run script — and why it has now
been **removed**: the master-catalog model replaces it, and re-running it would
re-create exactly the rows V22 prunes. A hospital's list is now built by the app
(baseline lazy-seed) and the admin (picker), never by a bulk seed.
