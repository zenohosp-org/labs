package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveSpecimenRequest {
    /** Defaults to now() when omitted. */
    private LocalDateTime receivedAt;
    private UUID receivedByUserId;
    private BigDecimal transportTemperatureC;
}
