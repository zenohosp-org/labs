package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRadiologyOrderRequest {
    private UUID hospitalId;
    private Integer patientId;
    private UUID admissionId;
    private String serviceName;
    private String specializationName;
    private UUID technicianId;
    private String technicianName;
    private String priority;
    private LocalDate scheduledDate;
    private String billNo;
    private BigDecimal price;

    /**
     * Phase 8.1 — GST % from the catalog (e.g. 18 for 18%). Added to
     * radiology_orders in V14 for parity with lab_orders. Drives the GST split
     * when the catalog row carries it; null = tax-free or legacy.
     */
    private BigDecimal gstRate;

    /**
     * Phase 8.1 — when present, server resolves the catalogue row, asserts
     * tenancy + discipline=RADIOLOGY, snapshots name/price/gstRate from the
     * catalog when request omits or differs (back-compat: null → free-text path).
     */
    private Long labServiceId;
}
