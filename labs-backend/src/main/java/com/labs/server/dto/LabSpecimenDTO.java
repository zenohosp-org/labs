package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabSpecimenDTO {
    private Long id;
    private Long labOrderId;
    private UUID hospitalId;

    private String containerType;
    private String additive;
    private BigDecimal volumeMl;

    private String barcode;
    private String qrPayload;

    private LocalDateTime collectedAt;
    private UUID collectedByUserId;
    private String collectedByName;
    private String collectionSite;

    private LocalDateTime receivedAt;
    private UUID receivedByUserId;
    private BigDecimal transportTemperatureC;

    private LocalDateTime accessionedAt;
    private UUID accessionedByUserId;

    private Boolean rejected;
    private LocalDateTime rejectedAt;
    private UUID rejectedByUserId;
    private String rejectionReasonCode;
    private String rejectionNotes;

    private String storageLocation;
    private LocalDateTime discardAt;
    private String notes;

    /** Derived: PENDING_COLLECTION | COLLECTED | RECEIVED | ACCESSIONED | REJECTED. */
    private String stage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
