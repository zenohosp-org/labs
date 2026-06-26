package com.labs.server.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportTemplateRequest {
    private String name;
    private String discipline;          // null = all
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
}
