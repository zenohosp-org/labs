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

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Column(name = "report_id", length = 20)
    private String reportId;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
