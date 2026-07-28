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
            @RequestParam(required = false) String status,
            Authentication auth) {
        return ResponseEntity.ok(radiologyService.getOrders(resolveHospitalId(auth), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RadiologyOrderDTO> getOrder(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(radiologyService.getOrder(id, resolveHospitalId(auth)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<RadiologyOrderDTO>> getByPatient(
            @PathVariable Integer patientId, Authentication auth) {
        return ResponseEntity.ok(radiologyService.getByPatient(patientId, resolveHospitalId(auth)));
    }

    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<List<RadiologyOrderDTO>> getByAdmission(
            @PathVariable UUID admissionId, Authentication auth) {
        return ResponseEntity.ok(radiologyService.getByAdmission(admissionId, resolveHospitalId(auth)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(Authentication auth) {
        UUID hospitalId = resolveHospitalId(auth);
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
    public ResponseEntity<RadiologyOrderDTO> markScanned(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(radiologyService.markScanned(id, resolveHospitalId(auth)));
    }

    /**
     * Phase 7 — tech started the modality run.
     * PENDING_SCAN → IN_PROGRESS, stamps started_at + actor.
     */
    @PatchMapping("/{id}/start")
    public ResponseEntity<RadiologyOrderDTO> markStarted(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(radiologyService.markStarted(id, resolveHospitalId(auth)));
    }

    @PatchMapping("/{id}/report")
    public ResponseEntity<RadiologyOrderDTO> generateReport(
            @PathVariable Long id,
            @RequestBody RadiologyReportRequest request,
            Authentication auth) {
        return ResponseEntity.ok(radiologyService.generateReport(id, request, resolveHospitalId(auth)));
    }

    /** Phase 9 — Mark Completed. Gated on findings text presence. */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<RadiologyOrderDTO> markCompleted(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(radiologyService.markCompleted(id, resolveHospitalId(auth)));
    }

    /** Phase 9 — soft cancel. Body is {"reason": "…"}. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<RadiologyOrderDTO> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication auth) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(radiologyService.cancelOrder(id, reason, resolveHospitalId(auth)));
    }

    /**
     * Tenant scope for every request, taken from the validated JWT rather than
     * a client-supplied param. Fails closed: no token or no hospital claim
     * means no request, instead of falling back to an unscoped query.
     */
    private UUID resolveHospitalId(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) {
            throw new RuntimeException("Unauthenticated");
        }
        UUID hospitalId = jwtUtil.getHospitalId((String) auth.getCredentials());
        if (hospitalId == null) {
            throw new RuntimeException("Token carries no hospital scope");
        }
        return hospitalId;
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
