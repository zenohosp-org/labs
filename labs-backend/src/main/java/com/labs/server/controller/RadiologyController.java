package com.labs.server.controller;

import com.labs.server.dto.CreateRadiologyOrderRequest;
import com.labs.server.dto.RadiologyOrderDTO;
import com.labs.server.dto.RadiologyReportRequest;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.RadiologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirror of HMS {@code RadiologyController}. Path stays {@code /api/radiology}
 * and JSON shapes are identical so the HMS frontend can swap its base URL to
 * api-labs without route or response changes.
 */
@RestController
@RequestMapping("/api/radiology")
@RequiredArgsConstructor
public class RadiologyController {

    private final RadiologyService radiologyService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<RadiologyOrderDTO>> getOrders(
            @RequestParam UUID hospitalId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(radiologyService.getOrders(hospitalId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RadiologyOrderDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(radiologyService.getOrder(id));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<RadiologyOrderDTO>> getByPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(radiologyService.getByPatient(patientId));
    }

    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<List<RadiologyOrderDTO>> getByAdmission(@PathVariable UUID admissionId) {
        return ResponseEntity.ok(radiologyService.getByAdmission(admissionId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(@RequestParam UUID hospitalId) {
        // Matches HMS keys character-for-character. `reportGenerated` is the
        // REPORT_GENERATED + BILLED union so auto-billed orders stay counted.
        return ResponseEntity.ok(Map.of(
                "pendingScan",     radiologyService.countByStatus(hospitalId, "PENDING_SCAN"),
                "awaitingReport",  radiologyService.countByStatus(hospitalId, "AWAITING_REPORT"),
                "reportGenerated", radiologyService.countCompletedReports(hospitalId)
        ));
    }

    @PostMapping
    public ResponseEntity<RadiologyOrderDTO> createOrder(
            @RequestBody CreateRadiologyOrderRequest request,
            Authentication auth) {
        return ResponseEntity.ok(radiologyService.createOrder(request, resolveFullName(auth)));
    }

    @PatchMapping("/{id}/scan")
    public ResponseEntity<RadiologyOrderDTO> markScanned(@PathVariable Long id) {
        return ResponseEntity.ok(radiologyService.markScanned(id));
    }

    @PatchMapping("/{id}/report")
    public ResponseEntity<RadiologyOrderDTO> generateReport(
            @PathVariable Long id,
            @RequestBody RadiologyReportRequest request) {
        return ResponseEntity.ok(radiologyService.generateReport(id, request));
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
