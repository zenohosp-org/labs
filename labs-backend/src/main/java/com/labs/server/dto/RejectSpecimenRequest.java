package com.labs.server.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectSpecimenRequest {
    /** Required — must match a row in lab_rejection_reason or a hospital-custom code. */
    private String reasonCode;
    private String reasonNotes;
    private UUID rejectedByUserId;
}
