-- V1 — Phase 0 marker. Intentionally near-empty.
--
-- Flyway is configured with baseline-on-migrate=true + baseline-version=0,
-- so the existing Supabase schema (created by Hibernate ddl-auto=update over
-- the labs entity graph) is recorded as V0 baseline. This file is the first
-- Flyway-managed migration on top of that baseline; it does nothing destructive
-- and exists purely so the flyway_schema_history table has a real V1 row to
-- anchor future migrations against.
SELECT 1;
