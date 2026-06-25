package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Controlled vocab of pre-analytical sample rejection reasons. Seeded by V4
 * with NABL-aligned defaults; hospitals can add their own rows.
 *
 * Code is a stable string identifier (no surrogate ID). LabSpecimen stores
 * the code directly — no FK constraint so labs can add custom codes per
 * hospital without a join table.
 */
@Entity
@Table(name = "lab_rejection_reason")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabRejectionReason {

    @Id
    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order")
    private Integer displayOrder;
}
