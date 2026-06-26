package com.labs.server.controller;

import com.labs.server.dto.ReportVerifyDTO;
import com.labs.server.service.ReportVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public verify endpoint — no authentication required.
 *
 * The QR on every report links to {portalBaseUrl}/report/verify/{token};
 * the labs frontend's public page fetches GET /api/report-verify/{token}
 * to render the verification result.
 *
 * Returns a minimal payload (patient initials + signatory + signedAt +
 * order accession) — no PHI beyond what the patient already holds.
 *
 * Security: SecurityConfig allowlists /api/report-verify/** ahead of the
 * /api/** authenticated() rule.
 */
@RestController
@RequestMapping("/api/report-verify")
@RequiredArgsConstructor
public class ReportVerifyController {

    private final ReportVerifyService service;

    @GetMapping("/{token}")
    public ResponseEntity<ReportVerifyDTO> verify(@PathVariable String token) {
        return ResponseEntity.ok(service.verify(token));
    }
}
