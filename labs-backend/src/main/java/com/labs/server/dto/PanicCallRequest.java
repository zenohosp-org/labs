package com.labs.server.dto;

import lombok.*;

/**
 * Records the panic-call communication for a critical result. NABL requires
 * every critical value to be communicated AND acknowledged.
 *
 * Two-step flow:
 *   PATCH /api/results/{id}/panic-call    — staff records who they called
 *   PATCH /api/results/{id}/panic-ack     — staff records who acknowledged
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanicCallRequest {
    /** Doctor / nurse contacted (free text — e.g. "Dr. Sharma, Ward 3"). */
    private String calledTo;
    /** Person who took the call on the other end (e.g. "Nurse Anitha"). */
    private String acknowledgedBy;
    private String comments;
}
