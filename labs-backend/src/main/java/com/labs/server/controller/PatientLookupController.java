package com.labs.server.controller;

import com.labs.server.dto.AdmissionDTO;
import com.labs.server.dto.PatientDTO;
import com.labs.server.dto.QuickPatientRequest;
import com.labs.server.service.PatientLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientLookupController {

    private final PatientLookupService patientLookupService;

    @GetMapping("/search")
    public ResponseEntity<List<PatientDTO>> search(
            @RequestParam UUID hospitalId,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(patientLookupService.search(hospitalId, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientDTO> get(@PathVariable Integer id) {
        return ResponseEntity.ok(patientLookupService.get(id));
    }

    @PostMapping
    public ResponseEntity<PatientDTO> quickCreate(@RequestBody QuickPatientRequest req) {
        return ResponseEntity.ok(patientLookupService.quickCreate(req));
    }

    @GetMapping("/{id}/admissions")
    public ResponseEntity<List<AdmissionDTO>> admissions(@PathVariable Integer id) {
        return ResponseEntity.ok(patientLookupService.getAdmissionsByPatient(id));
    }
}
