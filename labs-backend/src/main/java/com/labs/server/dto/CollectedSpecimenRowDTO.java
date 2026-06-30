package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row in the Collections page — joins a {@code lab_specimen} record back
 * to its owning {@code lab_order} + patient so the bench tech sees a single
 * scannable line (patient · UHID · test · sample · accession · collector ·
 * collected-at · status pill) without N round-trips.
 *
 * Includes a redundant {@code orderStatus} field so the UI can colour-code by
 * order-level lifecycle (PENDING_COLLECTION rows aren't here yet — that's the
 * Lab Queue's job — but IN_PROGRESS / REPORT_GENERATED / BILLED tells the
 * tech how far the order has moved past collection).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedSpecimenRowDTO {

    // ── Specimen identity ────────────────────────────────────────────────
    private Long specimenId;
    private String barcode;
    private String qrPayload;

    // ── Owning order ─────────────────────────────────────────────────────
    private Long labOrderId;
    private String serviceName;
    private String accessionNumber;
    private String orderStatus;          // PENDING_COLLECTION | AWAITING_REPORT | IN_PROGRESS | …
    private String priority;             // ROUTINE | URGENT | STAT — drives the priority pill

    // ── Container / volume ───────────────────────────────────────────────
    private String containerType;
    private String additive;
    private BigDecimal volumeMl;

    // ── Patient ──────────────────────────────────────────────────────────
    private Integer patientId;
    private String patientName;
    private String patientUhid;

    // ── Chain of custody ─────────────────────────────────────────────────
    private LocalDateTime collectedAt;
    private UUID collectedByUserId;
    private String collectedByName;
    private LocalDateTime receivedAt;
    private LocalDateTime accessionedAt;

    // ── Rejection (Boolean so the UI can render "—" vs explicit false) ──
    private Boolean rejected;
    private LocalDateTime rejectedAt;
    private String rejectionReasonCode;
    private String rejectionNotes;

    // ── Audit ────────────────────────────────────────────────────────────
    private LocalDateTime createdAt;
}
