package com.labs.server.controller;

import com.labs.server.dto.LabReferenceRangeRequest;
import com.labs.server.dto.RangeMatchDTO;
import com.labs.server.entity.LabReferenceRange;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.LabReferenceRangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for lab reference ranges (per-hospital). The catalogue is lazy-seeded
 * the first time a hospital reads {@code GET /} — so a brand-new hospital
 * sees a sensible starting set without an admin action.
 *
 * Tenant is always resolved from the JWT. Query-string {@code hospitalId} is
 * tolerated (matches the existing /api/* surface convention) but ignored
 * when the JWT carries a tenant.
 */
@RestController
@RequestMapping("/api/reference-ranges")
@RequiredArgsConstructor
public class LabReferenceRangeController {

    private final LabReferenceRangeService service;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<LabReferenceRange>> list(
            @RequestParam(required = false) UUID hospitalId,
            Authentication auth) {
        return ResponseEntity.ok(service.list(resolveHospitalId(auth, hospitalId)));
    }

    @PostMapping
    public ResponseEntity<LabReferenceRange> create(
            @RequestBody LabReferenceRangeRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.create(resolveHospitalId(auth, null), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabReferenceRange> update(
            @PathVariable UUID id,
            @RequestBody LabReferenceRangeRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.update(resolveHospitalId(auth, null), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        service.delete(resolveHospitalId(auth, null), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<LabReferenceRange> toggle(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.toggle(resolveHospitalId(auth, null), id));
    }

    /**
     * Match a measured value to the catalogue band. Used by the report-entry
     * UI to colour LOW/NORMAL/HIGH in real time.
     */
    @GetMapping("/match")
    public ResponseEntity<RangeMatchDTO> match(
            @RequestParam String testName,
            @RequestParam(required = false, defaultValue = "ANY") String sex,
            @RequestParam(required = false, defaultValue = "30") Integer ageYears,
            @RequestParam(required = false) BigDecimal value,
            Authentication auth) {
        UUID hid = resolveHospitalId(auth, null);
        return service.match(hid, testName, sex, ageYears != null ? ageYears : 30, value)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private UUID resolveHospitalId(Authentication auth, UUID fallback) {
        if (auth != null && auth.getCredentials() != null) {
            try {
                UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
                if (hid != null) return hid;
            } catch (Exception ignored) {
                // fall through to the request-supplied hospitalId
            }
        }
        if (fallback != null) return fallback;
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
