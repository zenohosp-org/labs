package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-hospital reference band for a lab investigation.
 *
 * Match precedence at result-entry time:
 *   1. testName matches (case-insensitive, exact)
 *   2. sex = result sex OR ANY
 *   3. ageYears falls inside [minAgeYears, maxAgeYears] (inclusive; null = open)
 *
 * Result flag derivation:
 *   value &lt; minValue → LOW
 *   value &gt; maxValue → HIGH
 *   otherwise         → NORMAL
 *
 * rangeText is the display string (e.g. "13.5 – 17.5 g/dL", "Negative",
 * "&lt;200 mg/dL"). When numeric bands are present the UI uses them for the
 * flag; the text is shown verbatim on the report.
 *
 * Owned by labs; lives in a new {@code lab_reference_ranges} table so the
 * change is purely additive — no existing HMS table is touched.
 */
@Entity
@Table(name = "lab_reference_ranges", indexes = {
    @Index(name = "idx_lab_ref_ranges_hospital", columnList = "hospital_id"),
    @Index(name = "idx_lab_ref_ranges_test_name", columnList = "hospital_id, test_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabReferenceRange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    @Column(length = 50)
    private String category;

    /** MALE | FEMALE | ANY. Stored as plain text for forward-compat with intersex bands. */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String sex = "ANY";

    @Column(name = "min_age_years")
    private Integer minAgeYears;

    @Column(name = "max_age_years")
    private Integer maxAgeYears;

    @Column(name = "min_value", precision = 12, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 12, scale = 4)
    private BigDecimal maxValue;

    @Column(length = 50)
    private String unit;

    @Column(name = "range_text", length = 100, nullable = false)
    private String rangeText;

    // ── Phase 2 additions (V8 migration) — all nullable so existing rows keep
    // matching unchanged. Match algorithm in LabReferenceRangeService prefers
    // the most-specific row (special_state + critical limits when set).

    /** Values strictly below this trigger HL7 LL (panic-low). */
    @Column(name = "critical_low", precision = 12, scale = 4)
    private BigDecimal criticalLow;

    /** Values strictly above this trigger HL7 HH (panic-high). */
    @Column(name = "critical_high", precision = 12, scale = 4)
    private BigDecimal criticalHigh;

    /** PREGNANT | NEONATE | FASTING | POSTPRANDIAL — null = baseline. */
    @Column(name = "special_state", length = 30)
    private String specialState;

    @Column(name = "loinc_code", length = 20)
    private String loincCode;

    @Column(name = "method", length = 80)
    private String method;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /** null = currently in force; set when this band is superseded. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "source_citation", length = 300)
    private String sourceCitation;

    /**
     * Phase 3 — FK to lab_test_catalog.id. When set, this range is OWNED by
     * the test (cascade-deleted with it). Match queries can short-circuit
     * the free-text testName lookup. NULL on legacy rows; V10 backfills by
     * name match.
     */
    @Column(name = "lab_test_id")
    private Long labTestId;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
