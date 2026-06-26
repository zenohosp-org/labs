package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Single-analyte result entry. Sent one-at-a-time or wrapped in
 * {@link BulkResultRequest} for whole-panel entry.
 *
 * Most fields optional — at minimum the tech supplies {@code testCode} and
 * either {@code valueNumeric} or {@code valueText}. The service:
 *  - resolves {@code analyteName}, {@code loincCode}, {@code unit}, {@code method}
 *    from the {@link com.labs.server.entity.LabService} row when present;
 *  - matches a reference range and snapshots low/high into the row;
 *  - derives {@code abnormalFlag} and {@code panicFlag} from the snapshot;
 *  - looks up the patient's prior FINAL value for {@code deltaFromPrevious}.
 *
 * Sets {@code resultStatus = PRELIMINARY}. Tech must hit /verify to move to FINAL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestResultRequest {
    /** Required. Must exist in lab_services for the order's hospital, or be a free-text code. */
    private String testCode;

    /** Optional override when the catalogue display name doesn't suit (e.g. local language). */
    private String analyteName;

    private BigDecimal valueNumeric;
    private String valueText;
    private String unit;
    private String method;
    private String instrumentId;
    private String reagentLot;
    private String comments;

    /** Pre-existing specimen this result was measured from. */
    private Long specimenId;

    /** Optional — defaults to caller's identity from JWT. */
    private UUID enteredByUserId;
    private String enteredByName;
}
