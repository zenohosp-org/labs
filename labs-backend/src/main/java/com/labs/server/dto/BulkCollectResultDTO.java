package com.labs.server.dto;

import lombok.*;

import java.util.List;

/**
 * Response after a successful bulk-collect. Carries the IDs of every
 * specimen created + every order transitioned so the UI can immediately
 * print barcodes / refresh stats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCollectResultDTO {
    private Integer patientId;
    private List<Long> collectedOrderIds;
    private List<LabSpecimenDTO> createdSpecimens;
    private int tubeCount;
    private int orderCount;
}
