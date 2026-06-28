package com.labs.server.service;

import com.labs.server.entity.Hospital;
import com.labs.server.util.HospitalIdPrefix;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Phase 10 — generates requisition numbers in the same format-family as
 * accession numbers: {HOSP-PREFIX}-REQ-{YYYY}-{6-digit-seq}.
 *
 * Uses a dedicated Postgres sequence ({@code requisition_number_seq}, V17)
 * for the monotonic counter so multiple concurrent batches never clash —
 * unlike the count-based accession seq which retries on rare collisions.
 *
 * Sequence call runs in REQUIRES_NEW so it commits even if the calling
 * batch transaction rolls back. That's intentional: we never reuse a
 * requisition number, so a rolled-back batch leaves a small gap rather
 * than risking a dupe on retry.
 */
@Service
@RequiredArgsConstructor
public class RequisitionNumberService {

    private static final int SEQ_DIGITS = 6;

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String allocate(Hospital hospital) {
        Object raw = em.createNativeQuery("SELECT nextval('requisition_number_seq')")
                .getSingleResult();
        long seq = ((Number) raw).longValue();
        // HospitalIdPrefix.of() already includes the trailing dash ("1001-")
        // so the format string here uses no leading dash — otherwise we end up
        // with "1001--REQ-…" double-dash. Mirrors LabSpecimenService's
        // "%sACC-…" format for visual consistency.
        String prefix = HospitalIdPrefix.of(hospital);
        int year = Year.now().getValue();
        return String.format("%sREQ-%d-%0" + SEQ_DIGITS + "d", prefix, year, seq);
    }
}
