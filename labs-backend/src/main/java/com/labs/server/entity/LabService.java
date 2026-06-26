package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-hospital authoritative list of testable analytes and panels —
 * the labs-side parallel of HMS's {@code hospital_services}.
 *
 * Phase 7 (V12) renamed the underlying table from {@code lab_test_catalog}
 * → {@code lab_services} so the naming mirrors hospital_services on the
 * HMS side. The hospital_service_id FK column still loosely points at the
 * billing row in HMS (no DB FK — HMS owns that table); labs adds the
 * analytical metadata (LOINC, container, fasting, method, panel
 * relationships) that doesn't belong in a billing catalogue.
 *
 * Opt-in for existing orders (they still carry free-text service_name)
 * — but the catalogue is required for per-analyte result entry to work
 * with proper LOINC coding, default units, and panel → child-analyte
 * expansion.
 *
 * A panel row has {@code isPanel=true}; its child analytes are separate
 * catalogue rows with {@code parentPanelCode} pointing back at the
 * panel's {@code testCode}. The expansion is done in service-layer code,
 * not at the JPA level — keeping the panel/child relationship loose so a
 * child analyte can belong to multiple panels (HDL is part of both Lipid
 * Profile and Cardiac Risk Panel).
 */
@Entity
@Table(name = "lab_services",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lab_services_hospital_code",
                columnNames = {"hospital_id", "test_code"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "test_code", nullable = false, length = 50)
    private String testCode;

    @Column(name = "loinc_code", length = 20)
    private String loincCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "aliases", length = 500)
    private String aliases;

    @Column(name = "category", length = 50)
    private String category;

    /** PATHOLOGY | RADIOLOGY | CYTOLOGY | HISTOPATHOLOGY — drives the signoff rule. */
    @Column(name = "discipline", length = 30)
    private String discipline;

    /** BLOOD | URINE | STOOL | SWAB | CSF | TISSUE | OTHER. */
    @Column(name = "specimen_kind", length = 30)
    private String specimenKind;

    @Column(name = "default_container_type", length = 50)
    private String defaultContainerType;

    @Column(name = "default_additive", length = 50)
    private String defaultAdditive;

    @Column(name = "default_volume_ml", precision = 6, scale = 2)
    private BigDecimal defaultVolumeMl;

    @Column(name = "fasting_required", nullable = false)
    @Builder.Default
    private Boolean fastingRequired = false;

    @Column(name = "stability_minutes")
    private Integer stabilityMinutes;

    @Column(name = "default_method", length = 80)
    private String defaultMethod;

    @Column(name = "default_unit", length = 50)
    private String defaultUnit;

    /** NUMERIC | TEXT | CODED | RATIO. */
    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "NUMERIC";

    @Column(name = "requires_authorisation", nullable = false)
    @Builder.Default
    private Boolean requiresAuthorisation = false;

    @Column(name = "tat_minutes")
    private Integer tatMinutes;

    @Column(name = "is_panel", nullable = false)
    @Builder.Default
    private Boolean isPanel = false;

    @Column(name = "parent_panel_code", length = 50)
    private String parentPanelCode;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * Phase 3 — optional loose pointer to the HMS hospital_services row
     * that bills for this test. No FK constraint (HMS owns the table).
     * UI surfaces this so price changes on the HMS side stay in sync.
     */
    @Column(name = "hospital_service_id")
    private UUID hospitalServiceId;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
