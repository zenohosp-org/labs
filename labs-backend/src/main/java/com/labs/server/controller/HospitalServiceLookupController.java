package com.labs.server.controller;

import com.labs.server.dto.HospitalServiceDTO;
import com.labs.server.service.HospitalServiceLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hospital-services")
@RequiredArgsConstructor
public class HospitalServiceLookupController {

    private final HospitalServiceLookupService hospitalServiceLookupService;

    @GetMapping
    public ResponseEntity<List<HospitalServiceDTO>> list(@RequestParam UUID hospitalId) {
        return ResponseEntity.ok(hospitalServiceLookupService.list(hospitalId));
    }
}
