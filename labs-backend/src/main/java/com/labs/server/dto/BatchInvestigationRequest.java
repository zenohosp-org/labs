package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phase 10 — POST /api/investigations/batch payload.
 *
 * One submission = one requisition. The server allocates the requisition
 * number, routes each test to lab or radiology by the catalog row's
 * discipline, and wraps everything in a single transaction. If ANY test
 * fails validation (unknown labServiceId, wrong tenant, etc.), the whole
 * batch rolls back — no partial requisitions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchInvestigationRequest {

    private UUID hospitalId;
    private Integer patientId;
    private UUID admissionId;           // optional — IPD orders carry it
    private String priority;            // ROUTINE | URGENT | STAT — applies to every test
    private String referredByName;      // optional clinician display name

    private List<BatchTest> tests;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchTest {
        /** Required — catalog FK. Server resolves discipline + snapshots fields. */
        private Long labServiceId;

        /** Optional overrides — when present, win over catalog (back-compat with
         *  HMS during the pricing-authority transition). */
        private String serviceName;
        private String specializationName;
        private String sampleType;
        private BigDecimal price;
        private BigDecimal gstRate;
        private LocalDate scheduledDate;
    }
}
