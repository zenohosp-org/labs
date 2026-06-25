package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Specimen creation payload. Most fields optional — at minimum the collection
 * point only supplies {@code containerType} and we let the service generate a
 * barcode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSpecimenRequest {
    private String containerType;
    private String additive;
    private BigDecimal volumeMl;

    /** Optional — service generates one if blank, scoped to the order's hospital. */
    private String barcode;

    /** Defaults to now() when omitted (= phlebotomist drew it at the moment of the request). */
    private LocalDateTime collectedAt;
    private UUID collectedByUserId;
    private String collectedByName;
    private String collectionSite;

    private String notes;
}
