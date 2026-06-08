package com.labs.server.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabReportRequest {
    private String findings;
    private String observation;
}
