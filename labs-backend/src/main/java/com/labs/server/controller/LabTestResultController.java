package com.labs.server.controller;

import com.labs.server.dto.*;
import com.labs.server.service.LabTestResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 2 endpoints — per-analyte result lifecycle.
 *
 *   GET    /api/lab/{labOrderId}/results        — list results for an order
 *   POST   /api/lab/{labOrderId}/results        — single-analyte create (PRELIMINARY)
 *   POST   /api/lab/{labOrderId}/results/bulk   — whole-panel create
 *   GET    /api/results/{id}                    — one result
 *   PATCH  /api/results/{id}/verify             — tech sign-off → FINAL
 *   PATCH  /api/results/{id}/authorise          — pathologist sign-off (orthogonal)
 *   POST   /api/results/{id}/amend              — corrects a FINAL row (creates new CORRECTED row)
 *   PATCH  /api/results/{id}/cancel             — withdraw before release
 *   PATCH  /api/results/{id}/panic-call         — record the panic-call communication
 *
 * Coexists with the legacy {@code PATCH /api/lab/{id}/report} (text-blob
 * report) — both can be used; viewers prefer per-analyte rows when present.
 */
@RestController
@RequiredArgsConstructor
public class LabTestResultController {

    private final LabTestResultService resultService;

    @GetMapping("/api/lab/{labOrderId}/results")
    public ResponseEntity<List<LabTestResultDTO>> list(@PathVariable Long labOrderId) {
        return ResponseEntity.ok(resultService.listForOrder(labOrderId));
    }

    @PostMapping("/api/lab/{labOrderId}/results")
    public ResponseEntity<LabTestResultDTO> create(@PathVariable Long labOrderId,
                                                   @RequestBody CreateTestResultRequest req) {
        return ResponseEntity.ok(resultService.create(labOrderId, req));
    }

    @PostMapping("/api/lab/{labOrderId}/results/bulk")
    public ResponseEntity<List<LabTestResultDTO>> createBulk(@PathVariable Long labOrderId,
                                                             @RequestBody BulkResultRequest req) {
        return ResponseEntity.ok(resultService.createBulk(labOrderId, req));
    }

    @GetMapping("/api/results/{id}")
    public ResponseEntity<LabTestResultDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(resultService.get(id));
    }

    @PatchMapping("/api/results/{id}/verify")
    public ResponseEntity<LabTestResultDTO> verify(@PathVariable Long id,
                                                   @RequestBody(required = false) VerifyResultRequest req) {
        return ResponseEntity.ok(resultService.verify(id, req));
    }

    @PatchMapping("/api/results/{id}/authorise")
    public ResponseEntity<LabTestResultDTO> authorise(@PathVariable Long id,
                                                      @RequestBody(required = false) AuthoriseResultRequest req) {
        return ResponseEntity.ok(resultService.authorise(id, req));
    }

    @PostMapping("/api/results/{id}/amend")
    public ResponseEntity<LabTestResultDTO> amend(@PathVariable Long id,
                                                  @RequestBody AmendResultRequest req) {
        return ResponseEntity.ok(resultService.amend(id, req));
    }

    @PatchMapping("/api/results/{id}/cancel")
    public ResponseEntity<LabTestResultDTO> cancel(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(resultService.cancel(id, reason));
    }

    @PatchMapping("/api/results/{id}/panic-call")
    public ResponseEntity<LabTestResultDTO> panicCall(@PathVariable Long id,
                                                      @RequestBody(required = false) PanicCallRequest req) {
        return ResponseEntity.ok(resultService.recordPanicCall(id, req));
    }
}
