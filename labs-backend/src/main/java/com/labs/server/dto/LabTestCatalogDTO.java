package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestCatalogDTO {
    private Long id;
    private UUID hospitalId;

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

    /** Phase 3 — loose link to the HMS hospital_services row that bills this test. */
    private UUID hospitalServiceId;

    /** Phase 3 — number of reference-range bands attached to this test (computed). */
    private Long rangeCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
