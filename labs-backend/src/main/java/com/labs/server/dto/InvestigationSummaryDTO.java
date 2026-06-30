package com.labs.server.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified "investigation" projection used by the combined endpoint that
 * powers HMS's IPD Labs tab + the consultation-view Labs panel. A single
 * shape covering both {@code lab_orders} (pathology workflow) and
 * {@code radiology_orders} (imaging workflow), tagged via {@code kind}
 * so the consumer can route to the right detail page.
 *
 * Fields stay flat — no nested objects — so the HMS frontend can render
 * a single mixed list without juggling two shapes. The trade-off is a
 * couple of optional fields (sampleType, scannedAt) that are null for
 * the kind that doesn't carry them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationSummaryDTO {

    /** "LAB" or "RADIOLOGY" — what table this row originated from. */
    private String kind;

    /** Stable per-row id; numeric for parity with the source tables. */
    private Long id;

    private UUID hospitalId;
    private Integer patientId;
    private String patientName;
    private String patientUhid;
    private UUID admissionId;
    private String admissionNumber;

    /** Free-text name of the investigation (CBC, X-Ray Chest, etc.). */
    private String serviceName;
    private String specializationName;
    private String referredByName;

    private UUID technicianId;
    private String technicianName;

    private String priority;
    /**
     * Source-table status — clients should map per {@code kind}:
     *   LAB       → PENDING_COLLECTION | AWAITING_REPORT | IN_PROGRESS | REPORT_GENERATED | BILLED | CANCELLED
     *   RADIOLOGY → PENDING_SCAN       | AWAITING_REPORT | IN_PROGRESS | REPORT_GENERATED | BILLED | CANCELLED
     */
    private String status;
    private LocalDate scheduledDate;

    /**
     * Lab-only audit-trail identifier (Phase 1c).
     * Format: {HOSPITAL_NUMERIC_CODE}ACC-{YYYY}-{6-digit seq} — printed on
     * specimen labels + on the patient-facing report.
     */
    private String accessionNumber;

    /**
     * Phase 10 — group key shared by every row that landed in the same
     * POST /api/investigations/batch submission. Null on legacy single-test
     * orders created before V17. HMS uses this to render one card per
     * requisition in patient/admission lab lists.
     */
    private String requisitionNumber;

    /** LAB only — sample tube category. */
    private String sampleType;

    /** RADIOLOGY only — when the scan happened. */
    private LocalDateTime scannedAt;

    /** LAB only — when the sample was drawn / collected. */
    private LocalDateTime collectedAt;

    /** When the sample/imaging unit received custody (no status change — observational). */
    private LocalDateTime receivedAt;

    /** When the analyser run / read-out started — fires status → IN_PROGRESS. */
    private LocalDateTime startedAt;

    private LocalDateTime reportedAt;

    /** Set on soft cancel; pair with {@code cancellationReason} when present. */
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    private String findings;
    private String observation;
    private String reportId;
    private String createdByName;
    private LocalDateTime createdAt;

    // ── Payment surface (mirrors RadiologyOrderDTO) ──────────────────────
    private String invoiceStatus;
    private String invoiceNumber;
    private UUID invoiceId;
    private java.math.BigDecimal invoicePaid;
    private java.math.BigDecimal invoiceTotal;
}
