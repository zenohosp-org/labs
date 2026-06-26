package com.labs.server.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplateDTO {
    private Long id;
    private UUID hospitalId;

    private String name;
    private String discipline;
    private Boolean isDefault;

    private String logoUrl;
    private String headerHtml;
    private String footerHtml;
    private String accentColor;

    private String signatoryName;
    private String signatoryQualification;
    private String signatoryRegistration;
    private String signatureImageUrl;

    private String portalBaseUrl;

    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
