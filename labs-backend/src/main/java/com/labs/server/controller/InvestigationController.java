package com.labs.server.controller;

import com.labs.server.dto.BatchInvestigationRequest;
import com.labs.server.dto.BatchInvestigationResponse;
import com.labs.server.dto.InvestigationSummaryDTO;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.InvestigationBatchService;
import com.labs.server.service.InvestigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Combined lab + radiology read surface used by HMS to render its IPD Labs
 * tab and the consultation-view Labs panel as a single mixed list. Write
 * endpoints stay on {@code /api/lab} and {@code /api/radiology} for the
 * single-test path — Phase 10 adds {@code POST /batch} below for atomic
 * mixed-discipline multi-test creates that share a single requisition.
 */
@RestController
@RequestMapping("/api/investigations")
@RequiredArgsConstructor
public class InvestigationController {

    private final InvestigationService service;
    private final InvestigationBatchService batchService;
    private final JwtUtil jwtUtil;

    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<List<InvestigationSummaryDTO>> getByAdmission(@PathVariable UUID admissionId) {
        return ResponseEntity.ok(service.getByAdmission(admissionId));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<InvestigationSummaryDTO>> getByPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(service.getByPatient(patientId));
    }

    /**
     * Phase 10 — atomic mixed-discipline batch create. One requisition number,
     * N orders. Idempotency-Key header dedupes retries for 24h: a retried
     * submission with the same key returns the original {@link BatchInvestigationResponse}
     * with HTTP 200 instead of HTTP 201 (so HMS metrics can distinguish), but
     * the body is identical so client code treats both the same.
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchInvestigationResponse> createBatch(
            @RequestBody BatchInvestigationRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {

        String createdByName = resolveActorName(auth);
        BatchInvestigationResponse out = batchService.create(req, idempotencyKey, createdByName);
        return ResponseEntity
                .status(out.isIdempotent() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(out);
    }

    private String resolveActorName(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) return "System";
        try {
            String token = (String) auth.getCredentials();
            String email = jwtUtil.getEmail(token);
            return email != null ? email : "System";
        } catch (Exception e) {
            return "System";
        }
    }
}
