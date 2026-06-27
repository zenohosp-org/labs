-- V15 — extend audit_log.operation CHECK to admit Phase 7 lifecycle ops.
--
-- The original V2 CHECK didn't anticipate per-transition operation strings
-- (Phase 7 added IN_PROGRESS and the START/RECEIVE buttons, the report-sign
-- flow added SIGN/REVOKE). Code at LabService.markStarted, RadiologyService
-- equivalent, and the report-sign service write these operation strings,
-- and all three were silently failing with SQLSTATE 23514 ("new row …
-- violates check constraint chk_audit_log_operation"), then aborting the
-- whole transaction (Postgres autocommit is off in a Spring @Transactional
-- scope, so the failed audit INSERT poisoned the rest of the request).
--
-- Net effect today: every Start Test click on the lab queue returned HTTP
-- 500 with a downstream "current transaction is aborted" error and the
-- order never advanced past AWAITING_REPORT.
--
-- Fix: drop the old CHECK, add it back with START, RECEIVE, SIGN, REVOKE
-- explicitly. RECEIVE was already in the V2 list; including it again is a
-- no-op but keeps the migration self-documenting as the canonical set.
--
-- Idempotent: DROP CONSTRAINT IF EXISTS, ADD CONSTRAINT plain. Safe to
-- re-run if Flyway history is rewound.

ALTER TABLE audit_log
    DROP CONSTRAINT IF EXISTS chk_audit_log_operation;

ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_log_operation
    CHECK (operation IN (
        'CREATE',
        'UPDATE',
        'DELETE',
        'STATUS_CHANGE',
        'REPORT_GENERATED',
        'VERIFY',
        'AUTHORISE',
        'AMEND',
        'RECEIVE',
        'ACCESSION',
        'REJECT',
        'CANCEL',
        'PANIC_CALL',
        'TOGGLE',
        'AUTO_CREATE_ON_COLLECT',
        -- Phase 7 lifecycle additions:
        'START',          -- tech clicks Start Test → IN_PROGRESS
        'SIGN',           -- report sign-off
        'REVOKE'          -- report sign-off reversal
    ));

COMMENT ON COLUMN audit_log.operation IS
    'CREATE | UPDATE | DELETE | STATUS_CHANGE | REPORT_GENERATED | VERIFY | '
    'AUTHORISE | AMEND | RECEIVE | ACCESSION | REJECT | CANCEL | PANIC_CALL | '
    'TOGGLE | AUTO_CREATE_ON_COLLECT | START | SIGN | REVOKE';
