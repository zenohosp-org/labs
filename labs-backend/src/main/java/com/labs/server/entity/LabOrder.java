package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror of HMS RadiologyOrder, swapped for the lab domain:
 *   PENDING_SCAN          → PENDING_COLLECTION (sample to be drawn)
 *   AWAITING_REPORT       → AWAITING_REPORT    (sample processed, results pending)
 *   REPORT_GENERATED      → REPORT_GENERATED   (technologist finalised result)
 *   BILLED                → BILLED             (auto-billed into the patient invoice)
 *
 * `price` is captured at order creation so {@code generateReport} can auto-bill,
 * matching the radiology auto-bill flow on main.
 */
@Entity
@Table(name = "lab_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id")
    private Admission admission;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "specialization_name", length = 200)
    private String specializationName;

    @Column(name = "referred_by_name", length = 200)
    private String referredByName;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "technician_name", length = 200)
    private String technicianName;

    @Convert(converter = com.labs.server.converter.LabPriorityConverter.class)
    @Column(name = "priority_id")
    @Builder.Default
    private LabPriority priority = LabPriority.ROUTINE;

    @Convert(converter = com.labs.server.converter.LabStatusConverter.class)
    @Column(name = "status_id")
    @Builder.Default
    private LabStatus status = LabStatus.PENDING_COLLECTION;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "bill_no", length = 50)
    private String billNo;

    @Column(name = "sample_type", length = 100)
    private String sampleType;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * GST percentage captured at order time from the HospitalServices catalog
     * (e.g. {@code 18} for 18%). Drives the per-line GST split when
     * {@link com.labs.server.service.LabBillingService} auto-bills the order.
     * Null / zero means tax-free or pre-catalog rows — billing treats as 0%.
     */
    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate;

    /**
     * Phase 8.1 FK to the lab_services catalogue (V14). Nullable for back-compat:
     * legacy free-text orders + new orders that still arrive without a
     * labServiceId from HMS during the cutover keep working.
     */
    @Column(name = "lab_service_id")
    private Long labServiceId;

    /**
     * Phase 8.1 admin-triage classification (V14 CHECK).
     *   matched | ambiguous | invalid | legacy-misroute | NULL
     */
    @Column(name = "service_name_mapping_status", length = 20)
    private String serviceNameMappingStatus;

    // ── HIPAA-grade actor/timestamp triples for every status transition (V13).
    //    Every flip of `status` is mirrored by (xxx_at, xxx_by_user_id, xxx_by_name)
    //    so the operator UI can render "Collected 14:32 by Jane" from one row
    //    read, and audit_log keeps the tamper-evident history independently.

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "collected_by_user_id")
    private UUID collectedByUserId;

    @Column(name = "collected_by_name", length = 200)
    private String collectedByName;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "received_by_name", length = 200)
    private String receivedByName;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "started_by_user_id")
    private UUID startedByUserId;

    @Column(name = "started_by_name", length = 200)
    private String startedByName;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "reported_by_user_id")
    private UUID reportedByUserId;

    @Column(name = "reported_by_name", length = 200)
    private String reportedByName;

    // Phase 9 — soft-cancel actor/timestamp triple + optional reason text.
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancelled_by_name", length = 200)
    private String cancelledByName;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Column(name = "report_id", length = 20)
    private String reportId;

    /**
     * Phase 1 — lab-wide accession number printed on requisitions and specimen
     * barcodes. Nullable on legacy rows; LabService backfills on the next
     * lifecycle event (createOrder / markCollected) for any order that lacks
     * one. Uniqueness enforced via a partial unique index in V5.
     */
    @Column(name = "accession_number", length = 40)
    private String accessionNumber;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
