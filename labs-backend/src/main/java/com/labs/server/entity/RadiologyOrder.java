package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror of HMS {@code RadiologyOrder} pointing at the existing
 * {@code radiology_orders} table. Labs writes to the same table HMS does;
 * the HMS frontend will swap its base URL to {@code api-labs.zenohosp.com}
 * once this controller is live and the data source moves cleanly.
 */
@Entity
@Table(name = "radiology_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyOrder {

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

    @Convert(converter = com.labs.server.converter.RadiologyPriorityConverter.class)
    @Column(name = "priority_id")
    @Builder.Default
    private RadiologyPriority priority = RadiologyPriority.ROUTINE;

    @Convert(converter = com.labs.server.converter.RadiologyStatusConverter.class)
    @Column(name = "status_id")
    @Builder.Default
    private RadiologyStatus status = RadiologyStatus.PENDING_SCAN;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "bill_no", length = 50)
    private String billNo;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    // ── HIPAA-grade actor/timestamp triples (V13). Same shape as LabOrder.

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "scanned_by_user_id")
    private UUID scannedByUserId;

    @Column(name = "scanned_by_name", length = 200)
    private String scannedByName;

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
