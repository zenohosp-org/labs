package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per signed report event for a lab order.
 *
 * The PDF bytes themselves are NOT stored — every download renders fresh
 * from current result state. This keeps the artifact in sync with
 * amendments + cumulative additions naturally, and avoids a multi-MB
 * column scaling badly.
 *
 * What IS stored, append-only:
 *   - version (monotonic per lab_order)
 *   - supersedes_pdf_id (chain when amended)
 *   - signed_by_*, signed_at, signatory_snapshot (denormalised so a
 *     re-rendered historical PDF still shows its original signatory)
 *   - verify_token (random 32-byte hex) — embedded in the QR; public
 *     /api/report-verify/{token} returns minimal proof-of-authenticity
 *   - revoked flag for takedowns
 *
 * NABL audit trail: every change to the underlying lab_test_result rows
 * is already in audit_log, so the rendered PDF + the audit_log together
 * answer "what did the report say at time T".
 */
@Entity
@Table(name = "report_pdf", indexes = {
        @Index(name = "idx_report_pdf_order",    columnList = "lab_order_id, version DESC"),
        @Index(name = "idx_report_pdf_hospital", columnList = "hospital_id, created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportPdf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lab_order_id", nullable = false)
    private Long labOrderId;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** Chain back to the PDF this one supersedes (amendment / re-sign). */
    @Column(name = "supersedes_pdf_id")
    private Long supersedesPdfId;

    /** Frozen at render time — the template used so historical re-renders match. */
    @Column(name = "rendered_template_id")
    private Long renderedTemplateId;

    @Column(name = "signed_by_user_id")
    private UUID signedByUserId;

    @Column(name = "signed_by_name", length = 200)
    private String signedByName;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** JSONB snapshot of {name, qualification, registration} at render time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signatory_snapshot", columnDefinition = "jsonb")
    private String signatorySnapshot;

    /** Random 32-byte hex token embedded in the QR. Hit /api/report-verify/{token} to verify. */
    @Column(name = "verify_token", nullable = false, unique = true, length = 64)
    private String verifyToken;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "revoked_reason", length = 200)
    private String revokedReason;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_name", length = 200)
    private String revokedByName;

    /** TRUE when this render includes the patient's prior FINAL results for trending. */
    @Column(name = "cumulative_included", nullable = false)
    @Builder.Default
    private Boolean cumulativeIncluded = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (revoked == null) revoked = false;
        if (cumulativeIncluded == null) cumulativeIncluded = false;
        if (version == null) version = 1;
    }
}
