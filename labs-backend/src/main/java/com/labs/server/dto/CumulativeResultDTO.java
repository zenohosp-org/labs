package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cumulative report for a single patient + test_code — historical
 * FINAL/CORRECTED values plotted over time. Used by the report viewer's
 * trend chart and (optionally) included on the PDF when the patient has
 * prior values for any analyte in this order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CumulativeResultDTO {
    private String testCode;
    private String analyteName;
    private String loincCode;
    private String unit;
    private BigDecimal referenceLow;
    private BigDecimal referenceHigh;
    private String referenceText;

    /** Newest first. */
    private List<Point> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private Long resultId;
        private Long labOrderId;
        private BigDecimal value;
        private String abnormalFlag;
        private LocalDateTime at;          // verifiedAt || enteredAt || createdAt
        private String resultStatus;
    }
}
