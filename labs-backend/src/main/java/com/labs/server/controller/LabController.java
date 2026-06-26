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
     * Cancel a lab order. Mirrors HMS's existing IPD lab-orders cancel
     * semantics — only allowed before the sample is collected. Once the
     * sample is in the analyser the order is locked.
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
