package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One row per patient in the collection queue. Carries every pending lab
 * order for that patient + a pre-computed container plan so the
 * phlebotomist sees exactly which tubes to draw before clicking
 * "Collect all".
 *
 * Produced by {@link com.labs.server.service.CollectionService#buildQueue}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientCollectionPlanDTO {

    private Integer patientId;
    private String patientName;
    private String patientUhid;
    private String patientPhone;
    private LocalDate patientDob;
    private String patientSex;

    /** Wait time helper — earliest pending order's createdAt for this patient. */
    private LocalDateTime earliestPendingAt;
    private Integer ageYears;

    /**
     * STAT > URGENT > ROUTINE — derived as the highest priority across the
     * patient's pending orders so the UI can sort patients by clinical urgency.
     */
    private String highestPriority;

    /** Pending lab orders for this patient. */
    private List<OrderRef> orders;

    /** Aggregated tube plan — one row per distinct container. */
    private List<ContainerPlanItemDTO> containerPlan;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRef {
        private Long id;
        private String accessionNumber;
        private String serviceName;
        private String priority;
        private String sampleType;
        private String referredByName;
        private String specializationName;
        private BigDecimal price;
        private LocalDateTime createdAt;
        /** Resolved container type per orderID — same logic as containerPlan but per-order. */
        private String resolvedContainer;
        /** Whether the catalog flagged fasting required for this test. */
        private Boolean fastingRequired;
        private UUID hospitalId;
    }
}
