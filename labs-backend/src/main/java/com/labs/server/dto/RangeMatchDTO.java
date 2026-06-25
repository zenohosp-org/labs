package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of matching a measured value against the configured reference bands.
 * Returned by {@code GET /api/reference-ranges/match} so the UI can colour
 * the result inline without rebuilding the logic frontend-side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RangeMatchDTO {
    private UUID rangeId;
    private String testName;
    private String sex;
    private Integer minAgeYears;
    private Integer maxAgeYears;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private String rangeText;
    /** LOW | NORMAL | HIGH | null (no numeric bounds to compare against). Kept for backward compat. */
    private String flag;

    // ── Phase 2 additions ─────────────────────────────────────────────
    /** HL7 OBX-8: N | L | H | LL | HH | null. */
    private String abnormalFlag;
    /** TRUE when the value crossed criticalLow/criticalHigh. Drives panic-call workflow. */
    private Boolean panic;
    private BigDecimal criticalLow;
    private BigDecimal criticalHigh;
    private String specialState;
    private String loincCode;
    private String method;
}
