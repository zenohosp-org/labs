-- Phase 10 follow-up — switch accession_number generation off the count-based
-- approximation onto a real Postgres sequence.
--
-- Why now: Phase 10's POST /api/investigations/batch creates N lab_orders
-- back-to-back inside a single transaction. The old LabSpecimenService
-- algorithm derived its seq from orderRepository.count()+1+jitter, which
-- returns the SAME pre-batch number for every iteration in the loop
-- (Hibernate hasn't flushed the prior insert yet, or the count statement
-- snapshots before flush). Two same-millisecond accessions collide on the
-- V5 UNIQUE index and the whole batch rolls back. A real sequence sidesteps
-- the race entirely and matches what requisition_number_seq already does.
--
-- This is global across hospitals (the prefix in the formatted string
-- already namespaces them). Existing accessions keep their numbers — we
-- seed the new sequence past the current MAX so nothing collides on the
-- next allocation.

CREATE SEQUENCE IF NOT EXISTS accession_number_seq
  AS BIGINT
  START WITH 1
  INCREMENT BY 1
  NO CYCLE;

-- Seed past any existing accessions so the next allocation doesn't try to
-- reuse a number that's already in use. We don't try to parse the formatted
-- string (HOSP-PREFIX + 'ACC-' + YYYY + '-' + zeros + N) — instead we just
-- bump the seq past the row count, which is strictly safe given the old
-- algorithm derived from that same count.
DO $$
DECLARE
    seed BIGINT;
BEGIN
    SELECT COALESCE((SELECT COUNT(*) FROM lab_orders), 0) + 1000 INTO seed;
    PERFORM setval('accession_number_seq', seed, false);
END $$;
