package com.labs.server.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResultRequest {
    /** Optional — defaults to caller's identity from JWT. */
    private UUID verifiedByUserId;
    private String verifiedByName;
    private String comments;
}
