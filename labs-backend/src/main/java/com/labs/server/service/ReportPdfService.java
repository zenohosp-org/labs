package com.labs.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labs.server.context.AuthContext;
import com.labs.server.dto.CumulativeResultDTO;
import com.labs.server.dto.ReportPdfMetaDTO;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabTestResult;
import com.labs.server.entity.ReportPdf;
import com.labs.server.entity.ReportTemplate;
import com.labs.server.entity.ResultStatus;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabTestResultRepository;
import com.labs.server.repository.ReportPdfRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 5 — render a lab report as a signed PDF.
 *
 * Flow:
 *   1. {@link #sign(Long, boolean)} creates a new ReportPdf row (version =
 *      max(existing) + 1), snapshots the current signatory + mints a
 *      random verify_token. Returns the metadata.
 *   2. {@link #render(Long)} streams the actual PDF bytes by re-rendering
 *      the Thymeleaf template against the CURRENT order + result state.
 *      Idempotent — every call paints the latest data, which is intentional
 *      so amendments are reflected without a re-sign step.
 *   3. Public verify uses the verify_token to surface a minimal
 *      tamper-proof payload (see ReportVerifyService).
 *
 * No PDF bytes stored — render is cheap (~200 ms) and the underlying
 * result state + audit_log already provide the historical truth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RNG = new SecureRandom();

    // HL7 flags that count as panic — drive the banner on the PDF
    private static final Set<String> PANIC_FLAGS = Set.of("LL", "HH", "AA");

    // Result statuses we include on the report (skip CANCELLED, only show
    // the *latest* superseded amendment; original lines are in audit_log)
    private static final Set<ResultStatus> RENDERABLE = Set.of(
            ResultStatus.FINAL, ResultStatus.CORRECTED, ResultStatus.PRELIMINARY);

    private final ReportPdfRepository pdfRepository;
    private final LabOrderRepository orderRepository;
    private final LabTestResultRepository resultRepository;
    private final ReportTemplateService templateService;
    private final QrCodeService qrCodeService;
    private final CumulativeReportService cumulativeService;
    private final TemplateEngine templateEngine;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AuthContext> authContextProvider;

    // ── PUBLIC API ──────────────────────────────────────────────────────

    /**
     * Mint a new signed ReportPdf row for an order. version increments by
     * 1; supersedes_pdf_id chains back to the previous head if any. The
     * PDF bytes are NOT generated here — they're re-rendered on every
     * {@link #render(Long)} call.
     */
    @Transactional
    public ReportPdfMetaDTO sign(Long labOrderId, boolean includeCumulative) {
        LabOrder order = orderRepository.findById(labOrderId)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + labOrderId));
        UUID hospitalId = order.getHospital().getId();

        ReportTemplate template = templateService.resolveForRender(
                hospitalId, deriveDiscipline(order));

        Integer prevVersion = pdfRepository
                .findFirstByLabOrderIdOrderByVersionDesc(labOrderId)
                .map(ReportPdf::getVersion).orElse(0);

        String token = randomToken();
        AuthContext auth = authContextProvider.getIfAvailable();
        String signerName = (auth != null && auth.getEmail() != null) ? auth.getEmail() : "Authorised Signatory";
        UUID signerId = (auth != null && auth.getUserId() != null)
                ? safeUuid(auth.getUserId()) : null;

        ReportPdf row = ReportPdf.builder()
                .labOrderId(labOrderId)
                .hospitalId(hospitalId)
                .version(prevVersion + 1)
                .supersedesPdfId(prevVersion == 0 ? null :
                        pdfRepository.findFirstByLabOrderIdOrderByVersionDesc(labOrderId)
                                .map(ReportPdf::getId).orElse(null))
                .renderedTemplateId(template.getId())
                .signedByUserId(signerId)
                .signedByName(signerName)
                .signedAt(LocalDateTime.now())
                .signatorySnapshot(snapshotSignatory(template))
                .verifyToken(token)
                .cumulativeIncluded(includeCumulative)
                .build();

        ReportPdf saved = pdfRepository.save(row);
        auditService.record("ReportPdf", saved.getId().toString(), "SIGN",
                hospitalId, null, saved);
        return toMetaDTO(saved, template);
    }

    /**
     * Render the latest signed ReportPdf for an order to PDF bytes. If no
     * row exists yet, this is a no-op error — caller should sign first.
     */
    @Transactional(readOnly = true)
    public byte[] render(Long labOrderId) {
        ReportPdf head = pdfRepository.findFirstByLabOrderIdOrderByVersionDesc(labOrderId)
                .orElseThrow(() -> new RuntimeException(
                        "No signed report for order " + labOrderId + " — call /sign first"));
        return renderPdf(head);
    }

    /** Render a specific historical version. */
    @Transactional(readOnly = true)
    public byte[] renderVersion(Long pdfId) {
        ReportPdf row = pdfRepository.findById(pdfId)
                .orElseThrow(() -> new RuntimeException("Report PDF not found: " + pdfId));
        return renderPdf(row);
    }

    public List<ReportPdfMetaDTO> listForOrder(Long labOrderId) {
        List<ReportPdfMetaDTO> out = new ArrayList<>();
        for (ReportPdf row : pdfRepository.findByLabOrderIdOrderByVersionDesc(labOrderId)) {
            ReportTemplate template = row.getRenderedTemplateId() != null
                    ? templateService.resolveForRender(row.getHospitalId(), null)
                    : templateService.resolveForRender(row.getHospitalId(), null);
            out.add(toMetaDTO(row, template));
        }
        return out;
    }

    @Transactional
    public ReportPdfMetaDTO revoke(Long pdfId, String reason) {
        ReportPdf row = pdfRepository.findById(pdfId)
                .orElseThrow(() -> new RuntimeException("Report PDF not found: " + pdfId));
        if (Boolean.TRUE.equals(row.getRevoked())) {
            throw new RuntimeException("Report is already revoked");
        }
        AuthContext auth = authContextProvider.getIfAvailable();
        String byName = (auth != null && auth.getEmail() != null) ? auth.getEmail() : "Unknown";

        ReportPdf before = clone(row);
        row.setRevoked(true);
        row.setRevokedReason(reason);
        row.setRevokedAt(LocalDateTime.now());
        row.setRevokedByName(byName);
        ReportPdf saved = pdfRepository.save(row);
        auditService.record("ReportPdf", saved.getId().toString(), "REVOKE",
                saved.getHospitalId(), before, saved, "REVOKED", reason);

        ReportTemplate template = templateService.resolveForRender(saved.getHospitalId(), null);
        return toMetaDTO(saved, template);
    }

    // ── INTERNAL: actual rendering ──────────────────────────────────────

    private byte[] renderPdf(ReportPdf pdfRow) {
        LabOrder order = orderRepository.findById(pdfRow.getLabOrderId())
                .orElseThrow(() -> new RuntimeException("Lab order vanished — cannot render"));
        ReportTemplate template = templateService.resolveForRender(
                order.getHospital().getId(), deriveDiscipline(order));

        List<LabTestResult> rawResults = resultRepository.findByLabOrderIdOrderByCreatedAtAsc(pdfRow.getLabOrderId());
        List<RowVM> rows = buildRowsVM(rawResults);
        List<CumulativeResultDTO> cumulative = Boolean.TRUE.equals(pdfRow.getCumulativeIncluded())
                ? cumulativeService.forOrder(pdfRow.getLabOrderId())
                : List.of();

        boolean hasPanic = rows.stream().anyMatch(r ->
                r.abnormalFlag != null && PANIC_FLAGS.contains(r.abnormalFlag));

        // Build verify URL for the QR
        String verifyUrl = buildVerifyUrl(template, pdfRow.getVerifyToken());
        String qrDataUri = qrCodeService.dataUri(verifyUrl, 240);

        Context ctx = new Context();
        ctx.setVariable("hospital", Map.of(
                "name", order.getHospital() != null && order.getHospital().getName() != null
                        ? order.getHospital().getName() : "Hospital"
        ));
        ctx.setVariable("patient", Map.of(
                "name", patientName(order),
                "uhid", order.getPatient() != null ? order.getPatient().getUhid() : null,
                "ageDisplay", patientAgeDisplay(order),
                "sex", order.getPatient() != null ? order.getPatient().getGender() : null
        ));
        ctx.setVariable("order", order);
        ctx.setVariable("pdf", pdfRow);
        ctx.setVariable("template", template);
        ctx.setVariable("rows", rows);
        ctx.setVariable("cumulative", cumulative);
        ctx.setVariable("hasPanic", hasPanic);
        ctx.setVariable("panicCalledAt", findEarliestPanicCallAt(rawResults));
        ctx.setVariable("legacyFindings", order.getFindings());
        ctx.setVariable("legacyObservation", order.getObservation());
        ctx.setVariable("qrDataUri", qrDataUri);

        String html = templateEngine.process("lab_report", ctx);
        String xhtml = toXhtml(html);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF render failed for order {}: {}", pdfRow.getLabOrderId(), e.getMessage(), e);
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
    }

    /** Convert Thymeleaf HTML to well-formed XHTML (OpenHTMLtoPDF requires XML). */
    private String toXhtml(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false);
        return doc.html();
    }

    // ── VM building ────────────────────────────────────────────────────

    private List<RowVM> buildRowsVM(List<LabTestResult> raw) {
        List<RowVM> out = new ArrayList<>();
        for (LabTestResult r : raw) {
            if (r.getResultStatus() == null || !RENDERABLE.contains(r.getResultStatus())) continue;

            RowVM vm = new RowVM();
            vm.analyteName = r.getAnalyteName();
            vm.loincCode = r.getLoincCode();
            vm.unit = r.getUnit();
            vm.method = r.getMethod();
            vm.abnormalFlag = r.getAbnormalFlag();
            vm.displayValue = displayValue(r);
            vm.referenceDisplay = referenceDisplay(r);

            BigDecimal delta = r.getDeltaFromPrevious();
            if (delta != null) {
                vm.delta = delta;
                vm.deltaUp = delta.signum() > 0;
                vm.deltaDisplay = (delta.signum() > 0 ? "+" : "") + delta.toPlainString();
            }
            out.add(vm);
        }
        return out;
    }

    private String displayValue(LabTestResult r) {
        if (r.getValueNumeric() != null) return r.getValueNumeric().toPlainString();
        if (r.getValueText() != null && !r.getValueText().isBlank()) return r.getValueText();
        return "—";
    }

    private String referenceDisplay(LabTestResult r) {
        if (r.getReferenceText() != null && !r.getReferenceText().isBlank()) return r.getReferenceText();
        if (r.getReferenceLow() != null && r.getReferenceHigh() != null) {
            return r.getReferenceLow().toPlainString() + " – " + r.getReferenceHigh().toPlainString()
                    + (r.getUnit() != null ? " " + r.getUnit() : "");
        }
        return null;
    }

    private LocalDateTime findEarliestPanicCallAt(List<LabTestResult> raw) {
        LocalDateTime earliest = null;
        for (LabTestResult r : raw) {
            if (Boolean.TRUE.equals(r.getPanicFlag()) && r.getPanicCalledAt() != null) {
                if (earliest == null || r.getPanicCalledAt().isBefore(earliest)) earliest = r.getPanicCalledAt();
            }
        }
        return earliest;
    }

    private String patientName(LabOrder order) {
        if (order.getPatient() == null) return "Patient";
        String first = order.getPatient().getFirstName();
        String last = order.getPatient().getLastName();
        if (first == null && last == null) return "Patient";
        return ((first != null ? first : "") + (last != null ? " " + last : "")).trim();
    }

    private String patientAgeDisplay(LabOrder order) {
        if (order.getPatient() == null || order.getPatient().getDob() == null) return null;
        try {
            int years = Period.between(order.getPatient().getDob(), LocalDate.now()).getYears();
            return years + " yrs";
        } catch (Exception e) {
            return null;
        }
    }

    private String deriveDiscipline(LabOrder order) {
        // best-effort: order doesn't carry discipline directly; the catalogue
        // resolution falls back to the default template anyway. Reserved for
        // a future enrichment that joins lab_services by service_name.
        return null;
    }

    private String buildVerifyUrl(ReportTemplate template, String token) {
        String base = template.getPortalBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://labs.zenohosp.com";   // sensible default for prod
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/report/verify/" + token;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private String snapshotSignatory(ReportTemplate template) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "name", nullSafe(template.getSignatoryName()),
                    "qualification", nullSafe(template.getSignatoryQualification()),
                    "registration", nullSafe(template.getSignatoryRegistration())
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String randomToken() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return HEX.formatHex(buf);
    }

    private static UUID safeUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private ReportPdf clone(ReportPdf r) {
        return ReportPdf.builder()
                .id(r.getId())
                .labOrderId(r.getLabOrderId())
                .hospitalId(r.getHospitalId())
                .version(r.getVersion())
                .supersedesPdfId(r.getSupersedesPdfId())
                .renderedTemplateId(r.getRenderedTemplateId())
                .signedByUserId(r.getSignedByUserId())
                .signedByName(r.getSignedByName())
                .signedAt(r.getSignedAt())
                .signatorySnapshot(r.getSignatorySnapshot())
                .verifyToken(r.getVerifyToken())
                .revoked(r.getRevoked())
                .revokedReason(r.getRevokedReason())
                .revokedAt(r.getRevokedAt())
                .revokedByName(r.getRevokedByName())
                .cumulativeIncluded(r.getCumulativeIncluded())
                .createdAt(r.getCreatedAt())
                .build();
    }

    public ReportPdfMetaDTO toMetaDTO(ReportPdf row, ReportTemplate template) {
        String verifyUrl = buildVerifyUrl(template, row.getVerifyToken());
        return ReportPdfMetaDTO.builder()
                .id(row.getId())
                .labOrderId(row.getLabOrderId())
                .hospitalId(row.getHospitalId())
                .version(row.getVersion())
                .supersedesPdfId(row.getSupersedesPdfId())
                .renderedTemplateId(row.getRenderedTemplateId())
                .signedByUserId(row.getSignedByUserId())
                .signedByName(row.getSignedByName())
                .signedAt(row.getSignedAt())
                .verifyToken(row.getVerifyToken())
                .verifyUrl(verifyUrl)
                .revoked(row.getRevoked())
                .revokedReason(row.getRevokedReason())
                .revokedAt(row.getRevokedAt())
                .revokedByName(row.getRevokedByName())
                .cumulativeIncluded(row.getCumulativeIncluded())
                .createdAt(row.getCreatedAt())
                .build();
    }

    // ── tiny view-model used by the template ────────────────────────────
    @lombok.Data
    public static class RowVM {
        public String analyteName;
        public String loincCode;
        public String unit;
        public String method;
        public String abnormalFlag;
        public String displayValue;
        public String referenceDisplay;
        public BigDecimal delta;
        public boolean deltaUp;
        public String deltaDisplay;
    }
}
