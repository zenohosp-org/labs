package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLabOrderRequest {
    private UUID hospitalId;
    private Integer patientId;
    private UUID admissionId;
    private String serviceName;
    private String specializationName;
    private UUID technicianId;
    private String technicianName;
    private String priority;
    private String sampleType;
    private LocalDate scheduledDate;
    private String billNo;
    private BigDecimal price;
    /** GST % from the HospitalServices catalog. e.g. 18 for 18%. */
    private BigDecimal gstRate;
}
