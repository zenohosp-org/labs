package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDTO {
    private Long id;
    private UUID hospitalId;
    private Integer patientId;
    private String patientName;
    private String patientUhid;
    private UUID admissionId;
    private String admissionNumber;
    private String serviceName;
    private String specializationName;
    private String referredByName;
    private UUID technicianId;
    private String technicianName;
    private String priority;
    private String status;
    private LocalDate scheduledDate;
    private String billNo;
    private String sampleType;
    private BigDecimal price;
    private BigDecimal gstRate;
    // ── HIPAA timestamp+actor triples (Phase 7 / V13) ────────────────────
    private LocalDateTime collectedAt;
    private UUID collectedByUserId;
    private String collectedByName;
    private LocalDateTime receivedAt;
    private UUID receivedByUserId;
    private String receivedByName;
    private LocalDateTime startedAt;
    private UUID startedByUserId;
    private String startedByName;
    private LocalDateTime reportedAt;
    private UUID reportedByUserId;
    private String reportedByName;

    private String findings;
    private String observation;
    private String reportId;

    /** Phase 1 — public lab-wide accession printed on barcodes / requisitions. */
    private String accessionNumber;

    private String createdByName;
    private LocalDateTime createdAt;
}
