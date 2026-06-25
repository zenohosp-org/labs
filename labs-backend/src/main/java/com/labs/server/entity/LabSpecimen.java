package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One physical container per row.
 *
 * A lab_order can have many specimens — a CBC + LFT + Lipid panel taken in
 * one draw is one order with three tubes (EDTA for CBC, plain for LFT/Lipid).
 * Each specimen has its own chain of custody: collected → received →
 * accessioned. Rejection short-circuits the chain at any point.
 *
 * Phase 1 keeps this entity simple — just the lifecycle metadata. Per-analyte
 * results land in Phase 2 with their own table (lab_test_result), each row
 * linked back to a specimen via specimen_id.
 */
@Entity
@Table(name = "lab_specimen")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabSpecimen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lab_order_id", nullable = false)
    private Long labOrderId;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "container_type", length = 50)
    private String containerType;

    @Column(name = "additive", length = 50)
    private String additive;

    @Column(name = "volume_ml", precision = 6, scale = 2)
    private BigDecimal volumeMl;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "qr_payload", length = 255)
    private String qrPayload;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "collected_by_user_id")
    private UUID collectedByUserId;

    @Column(name = "collected_by_name", length = 200)
    private String collectedByName;

    @Column(name = "collection_site", length = 100)
    private String collectionSite;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "transport_temperature_c", precision = 4, scale = 1)
    private BigDecimal transportTemperatureC;

    @Column(name = "accessioned_at")
    private LocalDateTime accessionedAt;

    @Column(name = "accessioned_by_user_id")
    private UUID accessionedByUserId;

    @Column(name = "rejected", nullable = false)
    @Builder.Default
    private Boolean rejected = false;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;

    @Column(name = "rejection_reason_code", length = 50)
    private String rejectionReasonCode;

    @Column(name = "rejection_notes", columnDefinition = "TEXT")
    private String rejectionNotes;

    @Column(name = "storage_location", length = 100)
    private String storageLocation;

    @Column(name = "discard_at")
    private LocalDateTime discardAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (rejected == null) rejected = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
