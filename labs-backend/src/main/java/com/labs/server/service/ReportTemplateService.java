package com.labs.server.service;

import com.labs.server.dto.CreateReportTemplateRequest;
import com.labs.server.dto.ReportTemplateDTO;
import com.labs.server.entity.ReportTemplate;
import com.labs.server.repository.ReportTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-hospital report template CRUD + the resolver that picks which
 * template a render should use.
 *
 * Resolution precedence:
 *   1. discipline match + active
 *   2. default (discipline NULL) + active
 *   3. fallback: synthesise a minimal in-memory template so a hospital
 *      that hasn't configured one yet still gets a usable PDF
 *
 * Audits every CUD via AuditService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportTemplateService {

    private final ReportTemplateRepository repository;
    private final AuditService auditService;

    public List<ReportTemplateDTO> list(UUID hospitalId) {
        return repository.findByHospitalIdOrderByIsDefaultDescNameAsc(hospitalId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Picks the right template for a render. Never returns null — falls back
     * to a minimal synthesised template if the hospital has none configured.
     */
    public ReportTemplate resolveForRender(UUID hospitalId, String discipline) {
        if (discipline != null && !discipline.isBlank()) {
            Optional<ReportTemplate> match = repository
                    .findFirstByHospitalIdAndDisciplineAndActiveTrueOrderByIsDefaultDescIdAsc(
                            hospitalId, discipline);
            if (match.isPresent()) return match.get();
        }
        Optional<ReportTemplate> def = repository
                .findFirstByHospitalIdAndIsDefaultTrueAndActiveTrueOrderByIdAsc(hospitalId);
        return def.orElseGet(() -> synthesizeFallback(hospitalId));
    }

    @Transactional
    public ReportTemplateDTO upsert(UUID hospitalId, Long id, CreateReportTemplateRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new RuntimeException("template name is required");
        }
        ReportTemplate row;
        ReportTemplate before = null;
        if (id != null) {
            row = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Template not found: " + id));
            if (!hospitalId.equals(row.getHospitalId())) {
                throw new RuntimeException("Template does not belong to this hospital");
            }
            before = clone(row);
        } else {
            row = ReportTemplate.builder().hospitalId(hospitalId).build();
        }

        // Single default per (hospital, discipline) — flip the others off
        // when a new template is marked default.
        boolean isDefault = req.getIsDefault() != null && req.getIsDefault();
        if (isDefault) {
            for (ReportTemplate other : repository.findByHospitalIdOrderByIsDefaultDescNameAsc(hospitalId)) {
                if ((row.getId() == null || !row.getId().equals(other.getId()))
                        && sameDiscipline(other.getDiscipline(), req.getDiscipline())) {
                    if (Boolean.TRUE.equals(other.getIsDefault())) {
                        other.setIsDefault(false);
                        repository.save(other);
                    }
                }
            }
        }

        row.setName(req.getName().trim());
        row.setDiscipline(blankToNull(req.getDiscipline()));
        row.setIsDefault(isDefault);
        row.setLogoUrl(blankToNull(req.getLogoUrl()));
        row.setHeaderHtml(req.getHeaderHtml());
        row.setFooterHtml(req.getFooterHtml());
        row.setAccentColor(blankToNull(req.getAccentColor()));
        row.setSignatoryName(blankToNull(req.getSignatoryName()));
        row.setSignatoryQualification(blankToNull(req.getSignatoryQualification()));
        row.setSignatoryRegistration(blankToNull(req.getSignatoryRegistration()));
        row.setSignatureImageUrl(blankToNull(req.getSignatureImageUrl()));
        row.setPortalBaseUrl(blankToNull(req.getPortalBaseUrl()));
        if (req.getActive() != null) row.setActive(req.getActive());

        ReportTemplate saved = repository.save(row);
        auditService.record("ReportTemplate", saved.getId().toString(),
                id == null ? "CREATE" : "UPDATE",
                hospitalId, before, saved);
        return toDTO(saved);
    }

    @Transactional
    public void delete(UUID hospitalId, Long id) {
        ReportTemplate row = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));
        if (!hospitalId.equals(row.getHospitalId())) {
            throw new RuntimeException("Template does not belong to this hospital");
        }
        repository.delete(row);
        auditService.record("ReportTemplate", String.valueOf(id), "DELETE", hospitalId, row, null);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private ReportTemplate synthesizeFallback(UUID hospitalId) {
        return ReportTemplate.builder()
                .hospitalId(hospitalId)
                .name("Default (auto)")
                .isDefault(true)
                .accentColor("#14b8a6")
                .headerHtml("<div style='text-align:center;'><strong>Laboratory Report</strong></div>")
                .footerHtml("<div style='text-align:center;font-size:10px;color:#666;'>"
                        + "Generated by ZenoLabs — for queries contact your lab.</div>")
                .signatoryName("Authorised Signatory")
                .active(true)
                .build();
    }

    private static boolean sameDiscipline(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private ReportTemplate clone(ReportTemplate r) {
        return ReportTemplate.builder()
                .id(r.getId())
                .hospitalId(r.getHospitalId())
                .name(r.getName())
                .discipline(r.getDiscipline())
                .isDefault(r.getIsDefault())
                .logoUrl(r.getLogoUrl())
                .headerHtml(r.getHeaderHtml())
                .footerHtml(r.getFooterHtml())
                .accentColor(r.getAccentColor())
                .signatoryName(r.getSignatoryName())
                .signatoryQualification(r.getSignatoryQualification())
                .signatoryRegistration(r.getSignatoryRegistration())
                .signatureImageUrl(r.getSignatureImageUrl())
                .portalBaseUrl(r.getPortalBaseUrl())
                .active(r.getActive())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    public ReportTemplateDTO toDTO(ReportTemplate r) {
        return ReportTemplateDTO.builder()
                .id(r.getId())
                .hospitalId(r.getHospitalId())
                .name(r.getName())
                .discipline(r.getDiscipline())
                .isDefault(r.getIsDefault())
                .logoUrl(r.getLogoUrl())
                .headerHtml(r.getHeaderHtml())
                .footerHtml(r.getFooterHtml())
                .accentColor(r.getAccentColor())
                .signatoryName(r.getSignatoryName())
                .signatoryQualification(r.getSignatoryQualification())
                .signatoryRegistration(r.getSignatoryRegistration())
                .signatureImageUrl(r.getSignatureImageUrl())
                .portalBaseUrl(r.getPortalBaseUrl())
                .active(r.getActive())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
