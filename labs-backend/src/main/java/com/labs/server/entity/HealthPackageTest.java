package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "health_package_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthPackageTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    @JsonIgnore
    private HealthPackage healthPackage;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    @Column(name = "test_category", length = 50)
    @Builder.Default
    private String testCategory = "GENERAL";

    @Column(name = "normal_range", length = 100)
    private String normalRange;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = true;

    /**
     * Phase 3 — FK to lab_test_catalog.id. Set when added via the catalogue
     * picker; NULL on legacy or unmatched rows. ON DELETE SET NULL preserves
     * the health-package row when the source test is deleted.
     */
    @Column(name = "lab_test_id")
    private Long labTestId;
}
