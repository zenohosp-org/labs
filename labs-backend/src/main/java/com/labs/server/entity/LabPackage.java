package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lab investigation bundle with combo pricing. Distinct from
 * {@link HealthPackage} — these are ad-hoc diagnostic bundles built by lab
 * staff (e.g. "Liver Function Profile" = SGPT + SGOT + Bilirubin + ALP +
 * Albumin at a flat combo rate) rather than wellness-checkup packages.
 *
 * Sits on a new {@code lab_packages} table so the change is purely additive
 * — no migration of existing HMS-owned tables.
 */
@Entity
@Table(name = "lab_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    @Builder.Default
    private String category = "GENERAL";

    /** Combo price — explicitly NOT the sum of item unit prices. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "validity_days")
    @Builder.Default
    private Integer validityDays = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "labPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<LabPackageItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
