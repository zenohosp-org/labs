package com.labs.server.controller;

import com.labs.server.dto.StaffDTO;
import com.labs.server.service.StaffLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class StaffLookupController {

    private final StaffLookupService staffLookupService;

    @GetMapping
    public ResponseEntity<List<StaffDTO>> list(@RequestParam UUID hospitalId) {
        return ResponseEntity.ok(staffLookupService.list(hospitalId));
    }
}
