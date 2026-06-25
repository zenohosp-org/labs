package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-analyte result row. One {@link LabOrder} can have many of these (a CBC
 * yields ~12 rows). Sits alongside lab_orders.findings/observation — the old
 * blob keeps working for legacy reports, while new clients write structured
 * rows here.
 *
 * State machine via {@link ResultStatus}:
 *   PENDING → PRELIMINARY → FINAL → (CORRECTED)
 *                                ↓
 *                           CANCELLED
 *
 * Authorisation (pathologist sign-off, required for HISTOPATHOLOGY etc.) is
 * orthogonal to the FSM: a FINAL row gains {@code authorisedByUserId} when
 * the pathologist signs. {@link LabTestCatalog#getRequiresAuthorisation()}
 * drives whether the workflow blocks release until that field is set.
 *
 * Amendments are immutable — when a FINAL row needs correction, a NEW row
 * is inserted with {@code amendmentOfId} pointing at the original. The
 * original is preserved; this is the NABL audit-trail requirement.
 */
@Entity
@Table(name = "lab_test_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lab_order_id", nullable = false)
    private Long labOrderId;

    @Column(name = "specimen_id")
    private Long specimenId;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "test_code", nullable = false, length = 50)
    private String testCode;

    @Column(name = "analyte_name", nullable = false, length = 200)
    private String analyteName;

    @Column(name = "loinc_code", length = 20)
    private String loincCode;

    @Column(name = "value_numeric", precision = 18, scale = 6)
    private BigDecimal valueNumeric;

    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "method", length = 80)
    private String method;

    @Column(name = "instrument_id", length = 80)
    private String instrumentId;

    @Column(name = "reagent_lot", length = 80)
    private String reagentLot;

    @Column(name = "reference_low", precision = 18, scale = 6)
    private BigDecimal referenceLow;

    @Column(name = "reference_high", precision = 18, scale = 6)
    private BigDecimal referenceHigh;

    @Column(name = "reference_text", length = 200)
    private String referenceText;

    /** HL7 OBX-8: N | L | H | LL | HH | A | AA. Stored as String for direct readability in DB tools. */
    @Column(name = "abnormal_flag", length = 4)
    private String abnormalFlag;

    @Column(name = "panic_flag", nullable = false)
    @Builder.Default
    private Boolean panicFlag = false;

    @Column(name = "delta_from_previous", precision = 18, scale = 6)
    private BigDecimal deltaFromPrevious;

    @Column(name = "delta_check_flag", length = 20)
    private String deltaCheckFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 20)
    @Builder.Default
    private ResultStatus resultStatus = ResultStatus.PENDING;

    @Column(name = "entered_by_user_id")
    private UUID enteredByUserId;

    @Column(name = "entered_by_name", length = 200)
    private String enteredByName;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_by_name", length = 200)
    private String verifiedByName;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "authorised_by_user_id")
    private UUID authorisedByUserId;

    @Column(name = "authorised_by_name", length = 200)
    private String authorisedByName;

    @Column(name = "authorised_at")
    private LocalDateTime authorisedAt;

    @Column(name = "amendment_of_id")
    private Long amendmentOfId;

    @Column(name = "amendment_reason_code", length = 50)
    private String amendmentReasonCode;

    @Column(name = "amendment_reason_notes", columnDefinition = "TEXT")
    private String amendmentReasonNotes;

    @Column(name = "panic_called_at")
    private LocalDateTime panicCalledAt;

    @Column(name = "panic_called_to", length = 200)
    private String panicCalledTo;

    @Column(name = "panic_acknowledged_by", length = 200)
    private String panicAcknowledgedBy;

    @Column(name = "panic_acknowledged_at")
    private LocalDateTime panicAcknowledgedAt;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (panicFlag == null) panicFlag = false;
        if (resultStatus == null) resultStatus = ResultStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
