package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;

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
}
