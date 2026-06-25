package com.labs.server.controller;

import com.labs.server.dto.CreateSpecimenRequest;
import com.labs.server.dto.LabSpecimenDTO;
import com.labs.server.dto.ReceiveSpecimenRequest;
import com.labs.server.dto.RejectSpecimenRequest;
import com.labs.server.service.LabSpecimenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Phase 1 endpoints — specimen chain of custody.
 *
 * Mounted under {@code /api/lab/{labOrderId}/specimens} for listing + create,
 * and under {@code /api/specimens/{id}/...} for per-specimen state transitions.
 * Old /api/lab/{id}/collect keeps working unchanged; specimens are additive.
 */
@RestController
@RequiredArgsConstructor
public class LabSpecimenController {

    private final LabSpecimenService specimenService;

    @GetMapping("/api/lab/{labOrderId}/specimens")
    public ResponseEntity<List<LabSpecimenDTO>> listForOrder(@PathVariable Long labOrderId) {
        return ResponseEntity.ok(specimenService.listForOrder(labOrderId));
    }

    @PostMapping("/api/lab/{labOrderId}/specimens")
    public ResponseEntity<LabSpecimenDTO> create(@PathVariable Long labOrderId,
                                                 @RequestBody CreateSpecimenRequest request) {
        return ResponseEntity.ok(specimenService.create(labOrderId, request));
    }

    @GetMapping("/api/specimens/{id}")
    public ResponseEntity<LabSpecimenDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(specimenService.get(id));
    }

    @PatchMapping("/api/specimens/{id}/receive")
    public ResponseEntity<LabSpecimenDTO> receive(@PathVariable Long id,
                                                  @RequestBody(required = false) ReceiveSpecimenRequest request) {
        return ResponseEntity.ok(specimenService.receive(id,
                request != null ? request : new ReceiveSpecimenRequest()));
    }

    @PatchMapping("/api/specimens/{id}/accession")
    public ResponseEntity<LabSpecimenDTO> accession(@PathVariable Long id,
                                                    @RequestParam(required = false) UUID accessionedByUserId) {
        return ResponseEntity.ok(specimenService.accession(id, accessionedByUserId));
    }

    @PatchMapping("/api/specimens/{id}/reject")
    public ResponseEntity<LabSpecimenDTO> reject(@PathVariable Long id,
                                                 @RequestBody RejectSpecimenRequest request) {
        return ResponseEntity.ok(specimenService.reject(id, request));
    }
}
