-- V17 — Phase 10: requisition groups + idempotency for the batch create endpoint.
--
-- Today HMS fires N independent POST /api/lab + /api/radiology calls for a
-- multi-test order. Labs sees N independent cards in the queue. Phase 10 adds:
--
--   1. requisition_number on both order tables — a shared group key so a doctor's
--      multi-test click renders as ONE card across labs + radiology queues. The
--      number is labs-owned, format {HOSP-PREFIX}-REQ-{YYYY}-{6-digit-seq},
--      mirroring accession_number. Nullable on both tables — single-test orders
--      and legacy rows stay ungrouped (UI shows them as one-test cards).
--
--   2. idempotency_keys table — guards the new POST /api/investigations/batch
--      endpoint against retries. HMS sends an Idempotency-Key header (one UUID
--      per submission, reused across retries of the same submission). Server
--      stores (hospital_id, idempotency_key) -> the original requisition + ids
--      for 24h and short-circuits dupes by returning the original response.
--      This is the difference between a duplicate-billing incident and a no-op.
--
-- HARD CONSTRAINT: requisition_number is queue/workflow grouping ONLY. Do NOT
-- touch invoice_items — every lab_order in a requisition still bills as its own
-- line, individually cancellable/refundable. The requisition card groups N
-- orders for the bench tech; it never collapses them into one charge.

ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS requisition_number VARCHAR(40);

ALTER TABLE radiology_orders
    ADD COLUMN IF NOT EXISTS requisition_number VARCHAR(40);

-- Partial indexes — only non-null requisitions participate in grouping; the
-- nullable column stays cheap for the legacy / single-test path.
CREATE INDEX IF NOT EXISTS idx_lab_orders_requisition
    ON lab_orders (requisition_number)
    WHERE requisition_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_radiology_orders_requisition
    ON radiology_orders (requisition_number)
    WHERE requisition_number IS NOT NULL;

COMMENT ON COLUMN lab_orders.requisition_number IS
    'Phase 10 group key. Shared by all lab + radiology_orders created in one batch. Nullable for legacy/single-test orders. Queue grouping only — never used by billing.';
COMMENT ON COLUMN radiology_orders.requisition_number IS
    'Phase 10 group key. Same semantics as lab_orders.requisition_number.';

-- ──────────────────────────────────────────────
-- Idempotency dedupe — keyed by (hospital_id, idempotency_key). Both lab + rad
-- order id arrays are stored as BIGINT[] so a mixed-discipline batch retry
-- returns the full original payload in one row read.
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS idempotency_keys (
    hospital_id         UUID         NOT NULL,
    idempotency_key     VARCHAR(120) NOT NULL,
    requisition_number  VARCHAR(40)  NOT NULL,
    lab_order_ids       BIGINT[]     NOT NULL DEFAULT '{}',
    radiology_order_ids BIGINT[]     NOT NULL DEFAULT '{}',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP    NOT NULL DEFAULT NOW() + INTERVAL '24 hours',
    PRIMARY KEY (hospital_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires
    ON idempotency_keys (expires_at);

COMMENT ON TABLE idempotency_keys IS
    'Phase 10 batch-endpoint dedupe. Row TTL is 24h via expires_at; cron can DELETE WHERE expires_at < NOW() to prune. PK is composite on (hospital_id, idempotency_key) so two tenants can use the same key value harmlessly.';

-- ──────────────────────────────────────────────
-- requisition_number sequence (labs-owned generator). One Postgres sequence
-- per year is overkill; one shared sequence is fine — the year + hospital prefix
-- in the formatted string provides natural human-readable grouping.
-- ──────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS requisition_number_seq START 1 INCREMENT 1;
COMMENT ON SEQUENCE requisition_number_seq IS
    'Phase 10 — monotonic integer feeding the {HOSP}-REQ-{YYYY}-{6-digit} formatter. No cache to keep the sequence dense in tests.';
