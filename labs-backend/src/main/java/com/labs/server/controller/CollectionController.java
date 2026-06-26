package com.labs.server.controller;

import com.labs.server.dto.BulkCollectRequest;
import com.labs.server.dto.BulkCollectResultDTO;
import com.labs.server.dto.CollectionStatsDTO;
import com.labs.server.dto.PatientCollectionPlanDTO;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Phase 6 — front-of-house collection console endpoints.
 *
 *   GET  /api/collection/queue
 *        Patient-grouped pending orders with per-patient tube plan.
 *   GET  /api/collection/queue/{patientId}
 *        Single patient's plan (used by the BulkCollectModal preview).
 *   POST /api/collection/bulk-collect
 *        Atomic — mark every order collected + create the tubes in one txn.
 *   GET  /api/collection/stats
 *        Counter dashboard tiles.
 */
@RestController
@RequestMapping("/api/collection")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService service;
    private final JwtUtil jwtUtil;

    @GetMapping("/queue")
    public ResponseEntity<List<PatientCollectionPlanDTO>> queue(Authentication auth) {
        return ResponseEntity.ok(service.buildQueue(resolveHospitalId(auth)));
    }

    @GetMapping("/queue/{patientId}")
    public ResponseEntity<PatientCollectionPlanDTO> patientPlan(
            @PathVariable Integer patientId, Authentication auth) {
        return service.getPlanForPatient(resolveHospitalId(auth), patientId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/bulk-collect")
    public ResponseEntity<BulkCollectResultDTO> bulkCollect(
            @RequestBody BulkCollectRequest req, Authentication auth) {
        if (req.getHospitalId() == null) req.setHospitalId(resolveHospitalId(auth));
        return ResponseEntity.ok(service.bulkCollect(req));
    }

    @GetMapping("/stats")
    public ResponseEntity<CollectionStatsDTO> stats(Authentication auth) {
        return ResponseEntity.ok(service.getStats(resolveHospitalId(auth)));
    }

    private UUID resolveHospitalId(Authentication auth) {
        if (auth != null && auth.getCredentials() != null) {
            UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
            if (hid != null) return hid;
        }
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
