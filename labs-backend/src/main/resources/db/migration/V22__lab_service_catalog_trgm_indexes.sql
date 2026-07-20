-- V22 — trigram search indexes for lab_service_catalog, built AFTER the V21
-- bulk load.
--
-- The "Add from catalog" picker runs ILIKE '%q%' over name + aliases on every
-- keystroke against ~60k rows; a GIN trigram index turns that from a full scan
-- into an index probe (verified: Bitmap Index Scan, not Seq Scan).
--
-- Split out from V20 on purpose: creating these GIN indexes on the already-
-- populated table takes a couple of seconds, whereas having them present while
-- V21's COPY streams 60k rows in makes the load ~75s slower (incremental GIN
-- maintenance per row). Load first (V21), index second (here).
--
-- pg_trgm is already installed in this shared DB (pharmacy uses it); IF NOT
-- EXISTS keeps a fresh environment self-sufficient.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_lab_service_catalog_name_trgm
    ON lab_service_catalog USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_lab_service_catalog_aliases_trgm
    ON lab_service_catalog USING gin (aliases gin_trgm_ops);
