package com.labs.server.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Public verify endpoint payload. Minimal — confirms the report exists,
 * was signed by whom, when, against which order. Does NOT expose patient
 * PHI to unauthenticated callers; only enough to PROVE the QR is genuine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportVerifyDTO {
    /** Always present — verified | revoked | not_found. */
    private String status;

    // Surfaced only when status == "verified" or "revoked":
    private Long labOrderId;
    private String accessionNumber;
    private String hospitalName;
    private String patientInitials;   // "V. K." instead of "Vasantha Kumari" to limit PII leak
    private String testSummary;       // e.g. "Complete Blood Count (CBC)"
    private Integer version;
    private LocalDateTime signedAt;
    private String signedByName;
    private String signatoryQualification;
    private String signatoryRegistration;

    // Only when status == "revoked":
    private LocalDateTime revokedAt;
    private String revokedReason;
}
