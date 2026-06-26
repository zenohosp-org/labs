package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-hospital branding metadata for rendered lab reports.
 *
 * One default template per hospital is sufficient for SMB tier; the schema
 * leaves room for per-discipline templates (PATHOLOGY vs HISTOPATHOLOGY
 * letterhead) by querying with both hospital_id + discipline (a
 * discipline-null row is the catch-all fallback).
 *
 * Signatory is stored inline. A future phase can add a child table
 * report_template_signatory when rotation is needed.
 */
@Entity
@Table(name = "report_template", indexes = {
        @Index(name = "idx_report_template_hospital", columnList = "hospital_id, active, is_default")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Nullable → applies to every discipline. PATHOLOGY|RADIOLOGY|CYTOLOGY|HISTOPATHOLOGY when scoped. */
    @Column(name = "discipline", length = 30)
    private String discipline;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = true;

    // ── Branding ────────────────────────────────────────────────────────
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "header_html", columnDefinition = "TEXT")
    private String headerHtml;

    @Column(name = "footer_html", columnDefinition = "TEXT")
    private String footerHtml;

    /** Hex / CSS colour token, e.g. '#14b8a6'. Drives accent strip + heading colour. */
    @Column(name = "accent_color", length = 20)
    private String accentColor;

    // ── Signatory ───────────────────────────────────────────────────────
    @Column(name = "signatory_name", length = 200)
    private String signatoryName;

    @Column(name = "signatory_qualification", length = 200)
    private String signatoryQualification;

    /** MCI / state registration number printed under the signature. */
    @Column(name = "signatory_registration", length = 80)
    private String signatoryRegistration;

    @Column(name = "signature_image_url", length = 500)
    private String signatureImageUrl;

    /** Base URL the verify QR points to; full URL = portalBaseUrl + "/report/verify/" + verifyToken. */
    @Column(name = "portal_base_url", length = 300)
    private String portalBaseUrl;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (isDefault == null) isDefault = true;
        if (active == null) active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
