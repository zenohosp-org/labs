package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDTO {
    private Long id;
    private UUID hospitalId;
    private Integer patientId;
    private String patientName;
    private String patientUhid;
    private UUID admissionId;
    private String admissionNumber;
    private String serviceName;
    private String specializationName;
    private String referredByName;
    private UUID technicianId;
    private String technicianName;
    private String priority;
    private String status;
    private LocalDate scheduledDate;
    private String billNo;
    private String sampleType;
    private BigDecimal price;
    private LocalDateTime collectedAt;
    private LocalDateTime reportedAt;
    private String findings;
    private String observation;
    private String reportId;
    private String createdByName;
    private LocalDateTime createdAt;
}
