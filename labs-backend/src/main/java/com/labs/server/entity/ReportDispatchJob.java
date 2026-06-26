package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pending + history of report deliveries.
 *
 * Phase 5 (this PR) inserts rows when staff requests a dispatch from the UI;
 * the actual channel adapters (WhatsApp Business / SMS / SMTP) land in
 * Phase 5b once the customer has a BSP wired up. PORTAL_LINK + PRINT
 * channels work today (PRINT is a no-op marker that the staff downloaded
 * the PDF + handed it to the patient; PORTAL_LINK marks "patient was given
 * the verify URL").
 *
 * Status: QUEUED → SENT | FAILED | CANCELLED.
 * Adapters retry on FAILED until {@code attempts} hits a per-channel cap.
 */
@Entity
@Table(name = "report_dispatch_job", indexes = {
        @Index(name = "idx_dispatch_pending", columnList = "status, channel, queued_at"),
        @Index(name = "idx_dispatch_order",   columnList = "lab_order_id, queued_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDispatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_pdf_id", nullable = false)
    private Long reportPdfId;

    @Column(name = "lab_order_id", nullable = false)
    private Long labOrderId;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    /** WHATSAPP | SMS | EMAIL | PORTAL_LINK | PRINT. */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** phone / email / 'patient_portal' / 'printed-counter' — channel-specific target. */
    @Column(name = "target", nullable = false, length = 300)
    private String target;

    /** QUEUED | SENT | FAILED | CANCELLED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "QUEUED";

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private LocalDateTime queuedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "requested_by_name", length = 200)
    private String requestedByName;

    @PrePersist
    protected void onCreate() {
        if (queuedAt == null) queuedAt = LocalDateTime.now();
        if (status == null) status = "QUEUED";
        if (attempts == null) attempts = 0;
    }
}
