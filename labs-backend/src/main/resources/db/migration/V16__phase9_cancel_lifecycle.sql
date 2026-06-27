-- V16 — Phase 9: lifecycle simplification + soft-cancel.
--
-- The Phase 7 lifecycle had: PENDING_COLLECTION → AWAITING_REPORT (via Receive
-- substep) → IN_PROGRESS → REPORT_GENERATED. Plus a per-analyte sign-off layer
-- (PRELIMINARY → FINAL → AUTHORISED → AMENDED) and a Specimens sub-tracker.
--
-- Operationally the receive step + sign-off ceremony + specimen tracker were
-- never adopted by phlebotomy / bench techs. They added clicks without adding
-- traceability we don't already get from V13 actor triples + audit_log.
--
-- Phase 9 collapses to:
--   Collection Queue  → Mark Collected + Print Labels + Cancel
--   Awaiting Reports  → Start Test + Cancel
--   In Progress       → Write Report + Mark Completed + Cancel
--   Reports           → final
--
-- Mark Completed checks for report data (analyte rows OR findings text) before
-- flipping IN_PROGRESS → REPORT_GENERATED; auto-bill runs as before.
--
-- This migration only adds the cancellation actor/timestamp triple columns —
-- LabStatus.CANCELLED(6) + RadiologyStatus.CANCELLED(6) are stored as the
-- integer 6 on status_id via the existing converter, no DB CHECK to update
-- (status_id is a plain integer column, not a string enum).

ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS cancelled_at         TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS cancelled_by_name    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS cancellation_reason  TEXT;

ALTER TABLE radiology_orders
    ADD COLUMN IF NOT EXISTS cancelled_at         TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS cancelled_by_name    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS cancellation_reason  TEXT;

COMMENT ON COLUMN lab_orders.cancelled_at IS
    'Phase 9 terminal cancel timestamp. Excluded from active queues; kept for HIPAA history (no DELETE).';
COMMENT ON COLUMN lab_orders.cancellation_reason IS
    'Optional free-text reason captured at cancel time. Mirrors NABL reason_code style.';
COMMENT ON COLUMN radiology_orders.cancelled_at IS
    'Phase 9 terminal cancel timestamp. Same semantics as lab_orders.cancelled_at.';
