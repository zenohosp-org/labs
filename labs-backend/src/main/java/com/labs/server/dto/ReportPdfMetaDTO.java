package com.labs.server.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metadata about a generated report. Returned by the report list /
 * sign endpoints — does NOT carry the PDF bytes. Bytes are fetched
 * via GET /api/lab/{id}/report.pdf (rendered on demand).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportPdfMetaDTO {
    private Long id;
    private Long labOrderId;
    private UUID hospitalId;

    private Integer version;
    private Long supersedesPdfId;

    private Long renderedTemplateId;
    private UUID signedByUserId;
    private String signedByName;
    private LocalDateTime signedAt;

    private String verifyToken;
    private String verifyUrl;          // computed: portalBaseUrl + "/report/verify/" + verifyToken
    private Boolean revoked;
    private String revokedReason;
    private LocalDateTime revokedAt;
    private String revokedByName;

    private Boolean cumulativeIncluded;
    private LocalDateTime createdAt;
}
