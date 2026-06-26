package com.labs.server.service;

import com.labs.server.dto.ReportVerifyDTO;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.ReportPdf;
import com.labs.server.entity.ReportTemplate;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.ReportPdfRepository;
import com.labs.server.repository.ReportTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Public-portal verify resolver. The endpoint is reachable WITHOUT
 * authentication (SecurityConfig allowlists /api/report-verify/**) so the
 * payload is deliberately minimal:
 *
 *   - no patient name (we surface initials only)
 *   - no result values
 *   - confirms the report exists + who signed + when + which order
 *
 * The QR's only purpose is to prove authenticity — viewing the report
 * itself still requires the patient to receive the PDF through a
 * trusted channel (printout, email, future WhatsApp).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportVerifyService {

    private final ReportPdfRepository pdfRepository;
    private final LabOrderRepository orderRepository;
    private final ReportTemplateRepository templateRepository;

    public ReportVerifyDTO verify(String token) {
        if (token == null || token.isBlank()) {
            return ReportVerifyDTO.builder().status("not_found").build();
        }
        Optional<ReportPdf> opt = pdfRepository.findByVerifyToken(token);
        if (opt.isEmpty()) {
            return ReportVerifyDTO.builder().status("not_found").build();
        }
        ReportPdf row = opt.get();
        LabOrder order = orderRepository.findById(row.getLabOrderId()).orElse(null);

        ReportVerifyDTO.ReportVerifyDTOBuilder b = ReportVerifyDTO.builder()
                .labOrderId(row.getLabOrderId())
                .version(row.getVersion())
                .signedAt(row.getSignedAt())
                .signedByName(row.getSignedByName());

        if (order != null) {
            b.accessionNumber(order.getAccessionNumber())
             .testSummary(order.getServiceName())
             .patientInitials(initials(order))
             .hospitalName(order.getHospital() != null ? order.getHospital().getName() : null);

            ReportTemplate template = row.getRenderedTemplateId() != null
                    ? templateRepository.findById(row.getRenderedTemplateId()).orElse(null)
                    : null;
            if (template != null) {
                b.signatoryQualification(template.getSignatoryQualification());
                b.signatoryRegistration(template.getSignatoryRegistration());
            }
        }

        if (Boolean.TRUE.equals(row.getRevoked())) {
            b.status("revoked")
             .revokedAt(row.getRevokedAt())
             .revokedReason(row.getRevokedReason());
        } else {
            b.status("verified");
        }
        return b.build();
    }

    private String initials(LabOrder order) {
        if (order.getPatient() == null) return "—";
        String f = order.getPatient().getFirstName();
        String l = order.getPatient().getLastName();
        StringBuilder out = new StringBuilder();
        if (f != null && !f.isBlank()) out.append(Character.toUpperCase(f.charAt(0))).append('.');
        if (l != null && !l.isBlank()) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(l.charAt(0))).append('.');
        }
        return out.length() == 0 ? "—" : out.toString();
    }
}
