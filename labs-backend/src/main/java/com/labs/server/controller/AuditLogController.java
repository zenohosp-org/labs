package com.labs.server.controller;

import com.labs.server.entity.AuditLog;
import com.labs.server.repository.AuditLogRepository;
import com.labs.server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only audit-trail viewer. Backs the Settings → Audit Trail page.
 *
 * Tenant scoping is always derived from the JWT — never trusted from the
 * query string — so a cross-hospital request returns the caller's own
 * audit rows, never another hospital's.
 */
@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository repository;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        UUID hid = resolveHospitalId(auth);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));

        if (entityType != null && !entityType.isBlank()
                && entityId != null && !entityId.isBlank()) {
            return ResponseEntity.ok(repository
                    .findByHospitalIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
                            hid, entityType.trim(), entityId.trim(), pageable));
        }
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            return ResponseEntity.ok(repository
                    .findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                            hid, LocalDateTime.parse(from), LocalDateTime.parse(to), pageable));
        }
        return ResponseEntity.ok(repository.findByHospitalIdOrderByOccurredAtDesc(hid, pageable));
    }

    private UUID resolveHospitalId(Authentication auth) {
        if (auth != null && auth.getCredentials() != null) {
            UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
            if (hid != null) return hid;
        }
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
