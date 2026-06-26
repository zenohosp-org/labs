package com.labs.server.controller;

import com.labs.server.dto.CumulativeResultDTO;
import com.labs.server.dto.ReportPdfMetaDTO;
import com.labs.server.service.CumulativeReportService;
import com.labs.server.service.ReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Lab-order report endpoints.
 *
 *   POST   /api/lab/{labOrderId}/report/sign?cumulative=true|false
 *          Mint a new signed ReportPdf row (version increments per order).
 *
 *   GET    /api/lab/{labOrderId}/report.pdf
 *          Stream the latest signed PDF (rendered on demand).
 *
 *   GET    /api/report-pdf/{pdfId}.pdf
 *          Stream a specific historical version.
 *
 *   GET    /api/lab/{labOrderId}/report/versions
 *          Metadata list — every version, newest first.
 *
 *   GET    /api/lab/{labOrderId}/cumulative
 *          Cumulative trend data per analyte (used by the frontend chart;
 *          also embedded in the PDF when /sign?cumulative=true).
 *
 *   POST   /api/report-pdf/{pdfId}/revoke
 *          Mark this version revoked; verify endpoint reports it as such.
 */
@RestController
@RequiredArgsConstructor
public class ReportPdfController {

    private final ReportPdfService pdfService;
    private final CumulativeReportService cumulativeService;

    @PostMapping("/api/lab/{labOrderId}/report/sign")
    public ResponseEntity<ReportPdfMetaDTO> sign(
            @PathVariable Long labOrderId,
            @RequestParam(name = "cumulative", required = false, defaultValue = "false") boolean cumulative) {
        return ResponseEntity.ok(pdfService.sign(labOrderId, cumulative));
    }

    @GetMapping(value = "/api/lab/{labOrderId}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadLatest(@PathVariable Long labOrderId) {
        byte[] bytes = pdfService.render(labOrderId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"lab-report-" + labOrderId + ".pdf\"")
                .body(bytes);
    }

    @GetMapping(value = "/api/report-pdf/{pdfId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long pdfId) {
        byte[] bytes = pdfService.renderVersion(pdfId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"lab-report-v" + pdfId + ".pdf\"")
                .body(bytes);
    }

    @GetMapping("/api/lab/{labOrderId}/report/versions")
    public ResponseEntity<List<ReportPdfMetaDTO>> versions(@PathVariable Long labOrderId) {
        return ResponseEntity.ok(pdfService.listForOrder(labOrderId));
    }

    @GetMapping("/api/lab/{labOrderId}/cumulative")
    public ResponseEntity<List<CumulativeResultDTO>> cumulative(@PathVariable Long labOrderId) {
        return ResponseEntity.ok(cumulativeService.forOrder(labOrderId));
    }

    @PostMapping("/api/report-pdf/{pdfId}/revoke")
    public ResponseEntity<ReportPdfMetaDTO> revoke(@PathVariable Long pdfId,
                                                   @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(pdfService.revoke(pdfId, reason));
    }
}
