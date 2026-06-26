package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lab_package_items", uniqueConstraints = {
    @UniqueConstraint(name = "uq_lab_package_items_pkg_investigation",
                      columnNames = {"package_id", "investigation_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabPackageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    @JsonIgnore
    private LabPackage labPackage;

    @Column(name = "investigation_name", nullable = false, length = 150)
    private String investigationName;

    /** PATHOLOGY | RADIOLOGY — used to route the order to the right queue when a package is booked. */
    @Column(name = "investigation_type", length = 20)
    @Builder.Default
    private String investigationType = "PATHOLOGY";

    @Column(length = 50)
    private String category;

    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * Phase 3 — FK to lab_test_catalog.id. Set when the package was authored
     * via the test picker; NULL on legacy / unmatched rows. ON DELETE SET NULL
     * at the DB level so deleting the source test preserves the package row
     * (investigation_name keeps the historical text).
     */
    @Column(name = "lab_test_id")
    private Long labTestId;
}
