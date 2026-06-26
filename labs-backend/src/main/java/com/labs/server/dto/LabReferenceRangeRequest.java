package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabReferenceRangeRequest {
    private String testName;
    private String category;
    /** MALE | FEMALE | ANY */
    private String sex;
    private Integer minAgeYears;
    private Integer maxAgeYears;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private String rangeText;
    private Boolean isActive;

    // ── Phase 2 extensions (V8) ─────────────────────────────────
    private BigDecimal criticalLow;
    private BigDecimal criticalHigh;
    private String specialState;
    private String loincCode;
    private String method;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sourceCitation;

    // ── Phase 3 link (V9) ───────────────────────────────────────
    /** FK to lab_services.id. When set, the test_name + unit auto-fill from the catalogue row. */
    private Long labServiceId;
}
