package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestResultDTO {
    private Long id;
    private Long labOrderId;
    private Long specimenId;
    private UUID hospitalId;

    private String testCode;
    private String analyteName;
    private String loincCode;

    private BigDecimal valueNumeric;
    private String valueText;
    private String unit;
    private String method;
    private String instrumentId;
    private String reagentLot;

    private BigDecimal referenceLow;
    private BigDecimal referenceHigh;
    private String referenceText;
    /** N | L | H | LL | HH | A | AA. */
    private String abnormalFlag;
    private Boolean panicFlag;
    private BigDecimal deltaFromPrevious;
    private String deltaCheckFlag;

    /** PENDING | PRELIMINARY | FINAL | CORRECTED | CANCELLED. */
    private String resultStatus;

    private UUID enteredByUserId;
    private String enteredByName;
    private LocalDateTime enteredAt;
    private UUID verifiedByUserId;
    private String verifiedByName;
    private LocalDateTime verifiedAt;
    private UUID authorisedByUserId;
    private String authorisedByName;
    private LocalDateTime authorisedAt;

    private Long amendmentOfId;
    private String amendmentReasonCode;
    private String amendmentReasonNotes;

    private LocalDateTime panicCalledAt;
    private String panicCalledTo;
    private String panicAcknowledgedBy;
    private LocalDateTime panicAcknowledgedAt;

    private String comments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
