-- V19 — widen audit_log.operation from VARCHAR(20) to VARCHAR(50).
--
-- V15 extended the CHECK constraint to admit longer Phase 7 operation names
-- (AUTO_CREATE_ON_COLLECT is 22 chars, REPORT_GENERATED is 16) but never
-- widened the underlying column type. Net effect: every Mark Collected click
-- on the lab queue threw PSQLException "value too long for type character
-- varying(20)", which aborted the transaction and surfaced as HTTP 500 with
-- a downstream "current transaction is aborted" cascade.
--
-- Repro:
--   PATCH /api/lab/{id}/collect →
--     LabService.markCollected →
--       LabSpecimenService.autoCreateForOrderIfMissing →
--         AuditService.record(..., "AUTO_CREATE_ON_COLLECT", ...) → 22 chars > 20.
--
-- Fix: widen to VARCHAR(50). Generous headroom for future operation names
-- (the longest reserved string today is REPORT_GENERATED at 16 chars; ceiling
-- at 50 gives room for ~30 more chars of operation taxonomy without another
-- migration).
--
-- The matching @Column(length = 50) annotation lives in AuditLog.java —
-- changed in the same commit so Hibernate's metadata stays consistent with
-- the database. ddl-auto=update would otherwise want to re-narrow on boot.
--
-- Idempotent: ALTER COLUMN TYPE on a wider compatible type is safe to re-run
-- (Postgres no-ops it when the target type already matches).

ALTER TABLE audit_log
    ALTER COLUMN operation TYPE VARCHAR(50);

COMMENT ON COLUMN audit_log.operation IS
    'CREATE | UPDATE | DELETE | STATUS_CHANGE | REPORT_GENERATED | VERIFY | '
    'AUTHORISE | AMEND | RECEIVE | ACCESSION | REJECT | CANCEL | PANIC_CALL | '
    'TOGGLE | AUTO_CREATE_ON_COLLECT | START | SIGN | REVOKE — '
    'see chk_audit_log_operation CHECK constraint for the live allowlist.';
