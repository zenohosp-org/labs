package com.labs.server.dto;

import lombok.*;

import java.util.List;

/**
 * Whole-panel entry. The frontend's result-entry table sends one of these
 * per panel (CBC, LFT, etc.) — service iterates and creates one
 * {@link com.labs.server.entity.LabTestResult} per analyte.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkResultRequest {
    private List<CreateTestResultRequest> results;
}
