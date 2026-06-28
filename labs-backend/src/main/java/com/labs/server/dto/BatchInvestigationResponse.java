package com.labs.server.dto;

import lombok.*;

import java.util.List;

/**
 * Phase 10 — POST /api/investigations/batch response. Returns the requisition
 * group key + the ids that landed in each pipeline. A mixed-discipline batch
 * populates both arrays; a pure-lab or pure-radiology batch populates only one.
 *
 * Same shape is returned on the FIRST call AND on every subsequent retry with
 * the same Idempotency-Key — HMS treats both responses identically.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchInvestigationResponse {
    private String requisitionNumber;
    private List<Long> labOrderIds;
    private List<Long> radiologyOrderIds;
    private boolean idempotent;     // true when this response came from the dedupe cache
}
