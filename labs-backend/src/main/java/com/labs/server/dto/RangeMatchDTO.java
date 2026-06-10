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
    /** LOW | NORMAL | HIGH | null (no numeric bounds to compare against) */
    private String flag;
}
