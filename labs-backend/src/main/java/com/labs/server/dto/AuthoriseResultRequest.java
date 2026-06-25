package com.labs.server.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthoriseResultRequest {
    /** Optional — defaults to caller's identity from JWT. Must be a pathologist for HISTOPATHOLOGY etc. */
    private UUID authorisedByUserId;
    private String authorisedByName;
    private String comments;
}
