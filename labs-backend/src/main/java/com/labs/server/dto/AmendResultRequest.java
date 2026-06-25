package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Amendment to a FINAL result. NEVER mutates the original row — service
 * inserts a new {@link com.labs.server.entity.LabTestResult} with
 * {@code amendmentOfId} pointing at the row being corrected and
 * {@code resultStatus = CORRECTED}.
 *
 * NABL requires a {@code reasonCode} on every amendment. Free-text notes
 * are encouraged but optional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmendResultRequest {
    /** Required. e.g. TRANSCRIPTION_ERROR | INSTRUMENT_RECAL | UNIT_CONVERSION | NEW_INFO. */
    private String reasonCode;
    private String reasonNotes;

    // New value — at least one of valueNumeric / valueText must be supplied.
    private BigDecimal valueNumeric;
    private String valueText;
    private String unit;
    private String method;
    private String comments;

    private UUID amendedByUserId;
    private String amendedByName;
}
