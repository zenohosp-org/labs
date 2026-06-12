package com.labs.server.controller;

import com.labs.server.dto.InvestigationSummaryDTO;
import com.labs.server.service.InvestigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Combined lab + radiology read surface used by HMS to render its IPD Labs
 * tab and the consultation-view Labs panel as a single mixed list. Write
 * endpoints stay on {@code /api/lab} and {@code /api/radiology} since
 * each kind has its own lifecycle (collect / scan / report).
 */
@RestController
@RequestMapping("/api/investigations")
@RequiredArgsConstructor
public class InvestigationController {

    private final InvestigationService service;

    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<List<InvestigationSummaryDTO>> getByAdmission(@PathVariable UUID admissionId) {
        return ResponseEntity.ok(service.getByAdmission(admissionId));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<InvestigationSummaryDTO>> getByPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(service.getByPatient(patientId));
    }
}
