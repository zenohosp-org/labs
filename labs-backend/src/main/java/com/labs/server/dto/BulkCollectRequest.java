package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One bulk-collect operation = one patient pickup at the counter.
 *
 * Atomic: server marks every order PENDING_COLLECTION → AWAITING_REPORT,
 * creates one specimen per tube row, audits each transition. If any single
 * order fails the whole transaction rolls back so the desk never gets a
 * half-collected state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCollectRequest {

    /** Required — every order in the call must belong to this patient. */
    private Integer patientId;

    /** Required — every order in the call must belong to this hospital. */
    private UUID hospitalId;

    /** Order IDs to collect. Caller usually passes ALL of a patient's pending orders. */
    private List<Long> orderIds;

    /** Tubes to materialise. Usually the same plan the queue endpoint returned, optionally trimmed. */
    private List<Tube> tubes;

    /** Free-text phlebotomist name; falls back to JWT email. */
    private String collectedByName;
    private UUID collectedByUserId;

    /** Defaults to now() server-side when omitted. */
    private LocalDateTime collectedAt;

    private String collectionSite;
    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tube {
        /** EDTA | CITRATE | HEPARIN | PLAIN | FLUORIDE | URINE_CUP | STOOL_CUP | SWAB | OTHER. */
        private String containerType;
        private String additive;
        private BigDecimal volumeMl;
        /** Optional barcode override; server generates if omitted. */
        private String barcode;
        /** Order IDs this tube serves — informational only; used to denormalise notes. */
        private List<Long> servesOrderIds;
    }
}
