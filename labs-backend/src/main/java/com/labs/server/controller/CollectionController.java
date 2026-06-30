package com.labs.server.controller;

import com.labs.server.dto.BulkCollectRequest;
import com.labs.server.dto.BulkCollectResultDTO;
import com.labs.server.dto.CollectedSpecimenRowDTO;
import com.labs.server.dto.CollectionStatsDTO;
import com.labs.server.dto.PatientCollectionPlanDTO;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * Date-windowed log of every specimen collected. Drives the Collections
     * page (post-collection audit view).
     *
     * @param from inclusive ISO date (yyyy-MM-dd). Default: today.
     * @param to   inclusive ISO date (yyyy-MM-dd). Default: today.
     */
    @GetMapping("/log")
    public ResponseEntity<List<CollectedSpecimenRowDTO>> log(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = from != null ? from : today;
        LocalDate toDate = to != null ? to : today;
        // Inclusive bounds — from = 00:00, to = 23:59:59.999.
        LocalDateTime fromTs = fromDate.atStartOfDay();
        LocalDateTime toTs = toDate.atTime(23, 59, 59, 999_999_999);
        return ResponseEntity.ok(service.log(resolveHospitalId(auth), fromTs, toTs));
    }

    private UUID resolveHospitalId(Authentication auth) {
        if (auth != null && auth.getCredentials() != null) {
            UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
            if (hid != null) return hid;
        }
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
