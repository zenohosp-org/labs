package com.labs.server.controller;

import com.labs.server.dto.CreateReportTemplateRequest;
import com.labs.server.dto.ReportTemplateDTO;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.ReportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Per-hospital report template admin.
 *
 *   GET    /api/report-templates
 *   POST   /api/report-templates           — create
 *   PUT    /api/report-templates/{id}      — update
 *   DELETE /api/report-templates/{id}
 */
@RestController
@RequestMapping("/api/report-templates")
@RequiredArgsConstructor
public class ReportTemplateController {

    private final ReportTemplateService service;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<ReportTemplateDTO>> list(Authentication auth) {
        return ResponseEntity.ok(service.list(resolveHospitalId(auth)));
    }

    @PostMapping
    public ResponseEntity<ReportTemplateDTO> create(@RequestBody CreateReportTemplateRequest req,
                                                    Authentication auth) {
        return ResponseEntity.ok(service.upsert(resolveHospitalId(auth), null, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportTemplateDTO> update(@PathVariable Long id,
                                                    @RequestBody CreateReportTemplateRequest req,
                                                    Authentication auth) {
        return ResponseEntity.ok(service.upsert(resolveHospitalId(auth), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(resolveHospitalId(auth), id);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveHospitalId(Authentication auth) {
        if (auth != null && auth.getCredentials() != null) {
            UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
            if (hid != null) return hid;
        }
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
