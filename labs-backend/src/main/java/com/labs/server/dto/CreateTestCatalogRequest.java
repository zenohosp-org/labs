package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Catalog row create / update payload. {@code hospitalId} comes from JWT or
 * a query param — kept off the body so a forged ID can't escape tenant scope.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestCatalogRequest {
    private String testCode;
    private String loincCode;
    private String name;
    private String aliases;

    private String category;
    private String discipline;
    private String specimenKind;

    private String defaultContainerType;
    private String defaultAdditive;
    private BigDecimal defaultVolumeMl;
    private Boolean fastingRequired;
    private Integer stabilityMinutes;

    private String defaultMethod;
    private String defaultUnit;
    private String valueType;

    private Boolean requiresAuthorisation;
    private Integer tatMinutes;
    private Boolean isPanel;
    private String parentPanelCode;

    private BigDecimal price;
    private BigDecimal gstRate;

    private Integer displayOrder;
    private Boolean active;
}
