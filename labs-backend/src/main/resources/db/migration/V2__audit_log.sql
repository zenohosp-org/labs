-- V2 — Phase 0: generic audit log for labs-owned mutations.
--
-- Foundational for HIPAA / NABL audit-trail requirements: every create / update
-- / delete on a labs-owned entity (lab orders, results, specimens, packages,
-- reference ranges, bookings) records a row with the before + after JSON snapshot,
-- the user that performed it, source IP, and an optional reason code (used for
-- amendments — NABL requires a reason on every result correction).
--
-- Write-once is enforced at the application layer initially; once we cut over
-- to a dedicated audit_writer DB role we'll revoke UPDATE/DELETE on this table
-- at the Postgres level.

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    hospital_id     UUID,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(64)  NOT NULL,
    operation       VARCHAR(20)  NOT NULL,    -- CREATE | UPDATE | DELETE | STATUS_CHANGE | READ_SENSITIVE
    user_id         UUID,
    user_email      VARCHAR(200),
    user_role       VARCHAR(50),
    source_ip       VARCHAR(45),
    user_agent      VARCHAR(500),
    old_value_json  JSONB,
    new_value_json  JSONB,
    reason_code     VARCHAR(50),
    reason_notes    TEXT,
    occurred_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_hospital_entity
    ON audit_log (hospital_id, entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at
    ON audit_log (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_user
    ON audit_log (user_id, occurred_at DESC);

COMMENT ON TABLE audit_log IS
    'Append-only audit trail for labs-owned mutations. Required for HIPAA + NABL 112A compliance.';
COMMENT ON COLUMN audit_log.operation IS
    'CREATE | UPDATE | DELETE | STATUS_CHANGE | READ_SENSITIVE';
COMMENT ON COLUMN audit_log.reason_code IS
    'NABL-mandated when amending a result; nullable for plain creates / updates.';
