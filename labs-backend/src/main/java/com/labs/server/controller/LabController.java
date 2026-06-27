package com.labs.server.controller;

import com.labs.server.dto.CreateLabOrderRequest;
import com.labs.server.dto.LabOrderDTO;
import com.labs.server.dto.LabReportRequest;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.LabService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirror of HMS RadiologyController. Routes use {@code /api/lab} so
 * frontend bindings stay 1:1 with the radiology API surface.
 */
@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabController {

    private final LabService labService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<LabOrderDTO>> getOrders(
            @RequestParam UUID hospitalId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(labService.getOrders(hospitalId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LabOrderDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(labService.getOrder(id));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<LabOrderDTO>> getByPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(labService.getByPatient(patientId));
    }

    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<List<LabOrderDTO>> getByAdmission(@PathVariable UUID admissionId) {
        return ResponseEntity.ok(labService.getByAdmission(admissionId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(@RequestParam UUID hospitalId) {
        return ResponseEntity.ok(Map.of(
                "pendingCollection", labService.countByStatus(hospitalId, "PENDING_COLLECTION"),
                "awaitingReport",    labService.countByStatus(hospitalId, "AWAITING_REPORT"),
                "reportGenerated",   labService.countCompletedReports(hospitalId)
        ));
    }

    @PostMapping
    public ResponseEntity<LabOrderDTO> createOrder(
            @RequestBody CreateLabOrderRequest request,
            Authentication auth) {
        return ResponseEntity.ok(labService.createOrder(request, resolveFullName(auth)));
    }

    @PatchMapping("/{id}/collect")
    public ResponseEntity<LabOrderDTO> markCollected(@PathVariable Long id) {
        return ResponseEntity.ok(labService.markCollected(id));
    }

    /**
     * Phase 7 — sample received at the lab receiving desk.
     * Stamps received_at + actor; status stays AWAITING_REPORT.
     */
    @PatchMapping("/{id}/receive")
    public ResponseEntity<LabOrderDTO> markReceived(@PathVariable Long id) {
        return ResponseEntity.ok(labService.markReceived(id));
    }

    /**
     * Phase 7 — tech started the analyser run.
     * AWAITING_REPORT → IN_PROGRESS, stamps started_at + actor.
     */
    @PatchMapping("/{id}/start")
    public ResponseEntity<LabOrderDTO> markStarted(@PathVariable Long id) {
        return ResponseEntity.ok(labService.markStarted(id));
    }

    @PatchMapping("/{id}/report")
    public ResponseEntity<LabOrderDTO> generateReport(
            @PathVariable Long id,
            @RequestBody LabReportRequest request) {
        return ResponseEntity.ok(labService.generateReport(id, request));
    }

    /**
     * Phase 9 — Mark Completed. IN_PROGRESS → REPORT_GENERATED gated on
     * report data presence (findings text OR at least one analyte result).
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<LabOrderDTO> markCompleted(@PathVariable Long id) {
        return ResponseEntity.ok(labService.markCompleted(id));
    }

    /**
     * Phase 9 — soft cancel. PENDING_COLLECTION / AWAITING_REPORT /
     * IN_PROGRESS → CANCELLED with optional reason. Body is {"reason": "…"}.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<LabOrderDTO> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(labService.cancelOrder(id, reason));
    }

    /**
     * Hard DELETE — only allowed when no clinical data exists (PENDING_COLLECTION,
     * never collected). For all other states use PATCH /cancel above. Legacy
     * endpoint retained for back-compat with HMS's existing IPD cancel button.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        labService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveFullName(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) return "System";
        try {
            String token = (String) auth.getCredentials();
            String email = jwtUtil.getEmail(token);
            return email != null ? email : "System";
        } catch (Exception e) {
            return auth.getName();
        }
    }
}
