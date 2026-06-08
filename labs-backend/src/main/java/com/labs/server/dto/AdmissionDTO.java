package com.labs.server.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionDTO {
    private UUID id;
    private String admissionNumber;
    private String ipdId;
    private LocalDateTime admissionDate;
    private LocalDateTime actualDischargeDate;
    private String status;
    private Integer patientId;
}
