package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Global, hospital-agnostic LOINC master catalog (table {@code
 * lab_service_catalog}, created in V20).
 *
 * This is the universe of possible tests — one shared copy, no
 * {@code hospitalId}. It is the labs parallel of pharmacy's
 * {@code pharmacy_medicine_catalog}: admins search it and copy a chosen term
 * into their hospital's own {@link LabService} list, rather than every hospital
 * carrying the whole LOINC set.
 *
 * Treated as read-only reference data — the app never writes it (the ~60k rows
 * are bulk-loaded by an ops seed script). It carries only the identity and
 * analytical fields that get copied onto a hospital's row when a term is added;
 * the hospital-owned fields (price, GST, TAT, container, active, …) are set by
 * the admin at add-time and live only on {@link LabService}.
 */
@Entity
@Table(name = "lab_service_catalog")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabServiceCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loinc_code", nullable = false, length = 20)
    private String loincCode;

    @Column(name = "test_code", length = 50)
    private String testCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "aliases", length = 500)
    private String aliases;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "discipline", length = 30)
    private String discipline;

    @Column(name = "specimen_kind", length = 30)
    private String specimenKind;

    @Column(name = "default_method", length = 80)
    private String defaultMethod;

    @Column(name = "default_unit", length = 50)
    private String defaultUnit;

    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "NUMERIC";

    @Column(name = "is_panel", nullable = false)
    @Builder.Default
    private Boolean isPanel = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
