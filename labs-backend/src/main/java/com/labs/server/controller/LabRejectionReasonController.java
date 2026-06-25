package com.labs.server.controller;

import com.labs.server.entity.LabRejectionReason;
import com.labs.server.repository.LabRejectionReasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only catalogue of pre-analytical rejection codes. The Phase 1b reject
 * modal in the frontend populates its dropdown from here. CRUD is out of
 * scope until a hospital actually needs custom codes; the seed is rich
 * enough to cover NABL defaults.
 */
@RestController
@RequestMapping("/api/lab-rejection-reasons")
@RequiredArgsConstructor
public class LabRejectionReasonController {

    private final LabRejectionReasonRepository repository;

    @GetMapping
    public ResponseEntity<List<LabRejectionReason>> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(activeOnly
                ? repository.findByActiveTrueOrderByDisplayOrderAsc()
                : repository.findAllByOrderByDisplayOrderAsc());
    }
}
