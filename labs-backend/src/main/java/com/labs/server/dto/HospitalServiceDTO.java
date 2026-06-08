package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalServiceDTO {
    private UUID id;
    private String name;
    private BigDecimal price;
    private BigDecimal gstRate;
    private Boolean isActive;
}
