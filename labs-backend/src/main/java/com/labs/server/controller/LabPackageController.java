package com.labs.server.controller;

import com.labs.server.entity.LabPackage;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.LabPackageService;
import com.labs.server.service.LabPackageService.PackageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for lab investigation packages. Mirrors the shape of
 * {@code /api/health-checkups/packages} so the frontend can reuse the same
 * PackageManager UI pattern.
 */
@RestController
@RequestMapping("/api/lab-packages")
@RequiredArgsConstructor
public class LabPackageController {

    private final LabPackageService service;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<LabPackage>> list(
            @RequestParam(required = false) UUID hospitalId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Authentication auth) {
        return ResponseEntity.ok(service.list(resolveHospitalId(auth, hospitalId), activeOnly));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('hospital_admin', 'super_admin')")
    public ResponseEntity<LabPackage> save(
            @RequestParam(required = false) UUID hospitalId,
            @RequestBody PackageRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.save(resolveHospitalId(auth, hospitalId), req));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('hospital_admin', 'super_admin')")
    public ResponseEntity<Void> toggle(@PathVariable UUID id, Authentication auth) {
        service.toggle(resolveHospitalId(auth, null), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('hospital_admin', 'super_admin')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        service.delete(resolveHospitalId(auth, null), id);
        return ResponseEntity.ok().build();
    }

    private UUID resolveHospitalId(Authentication auth, UUID fallback) {
        if (auth != null && auth.getCredentials() != null) {
            try {
                UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
                if (hid != null) return hid;
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (fallback != null) return fallback;
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
