package com.labs.server.controller;

import com.labs.server.entity.HealthCheckupBooking;
import com.labs.server.entity.HealthPackage;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.HealthCheckupService;
import com.labs.server.service.HealthCheckupService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirror of HMS {@code HealthCheckupController}. Endpoints + JSON shapes
 * are bit-identical so the HMS frontend can swap its base URL to labs
 * without route changes.
 *
 * <p>Tenant scoping: booking-scoped writes pull {@code hospitalId} from the
 * authenticated session via {@link #resolveHospitalId(Authentication)} so a
 * forged path parameter from another tenant can't act on a booking.
 */
@RestController
@RequestMapping("/api/health-checkups")
@RequiredArgsConstructor
public class HealthCheckupController {

    private final HealthCheckupService service;
    private final JwtUtil jwtUtil;

    // ── Packages ──────────────────────────────────────────────────────────

    @GetMapping("/packages")
    public ResponseEntity<List<HealthPackage>> getPackages(
            @RequestParam UUID hospitalId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(service.getPackages(hospitalId, activeOnly));
    }

    @PostMapping("/packages")
    @PreAuthorize("hasAnyRole('hospital_admin', 'super_admin')")
    public ResponseEntity<HealthPackage> savePackage(
            @RequestParam UUID hospitalId,
            @RequestBody PackageRequest req) {
        return ResponseEntity.ok(service.savePackage(hospitalId, req));
    }

    @PatchMapping("/packages/{id}/toggle")
    @PreAuthorize("hasAnyRole('hospital_admin', 'super_admin')")
    public ResponseEntity<Void> togglePackage(@PathVariable UUID id, Authentication auth) {
        service.togglePackage(resolveHospitalId(auth), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/packages/{id}")
    @PreAuthorize("hasAnyRole('hospital_admin', 'super_admin')")
    public ResponseEntity<Void> deletePackage(@PathVariable UUID id, Authentication auth) {
        service.deletePackage(resolveHospitalId(auth), id);
        return ResponseEntity.ok().build();
    }

    // ── Bookings ──────────────────────────────────────────────────────────

    @GetMapping("/bookings")
    public ResponseEntity<List<HealthCheckupBooking>> getBookings(
            @RequestParam UUID hospitalId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(service.getBookings(hospitalId, status, date));
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<HealthCheckupBooking> getBooking(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getBooking(id, resolveHospitalId(auth)));
    }

    @PostMapping("/bookings")
    public ResponseEntity<HealthCheckupBooking> createBooking(
            @RequestParam UUID hospitalId,
            @RequestBody BookingRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.createBooking(hospitalId, req, resolveFullName(auth)));
    }

    @PatchMapping("/bookings/{id}/status")
    public ResponseEntity<HealthCheckupBooking> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(service.updateStatus(id, resolveHospitalId(auth), body.get("status")));
    }

    @PatchMapping("/bookings/{id}/results/{resultId}")
    public ResponseEntity<HealthCheckupBooking> updateResult(
            @PathVariable UUID id,
            @PathVariable Long resultId,
            @RequestBody ResultUpdateRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.updateResult(id, resolveHospitalId(auth), resultId, req));
    }

    @PatchMapping("/bookings/{id}/doctor")
    public ResponseEntity<HealthCheckupBooking> assignDoctor(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        UUID doctorId = body.get("doctorId") != null && !body.get("doctorId").isBlank()
                ? UUID.fromString(body.get("doctorId")) : null;
        return ResponseEntity.ok(service.assignDoctor(id, resolveHospitalId(auth), doctorId));
    }

    @PatchMapping("/bookings/{id}/doctor-notes")
    public ResponseEntity<HealthCheckupBooking> saveDoctorNotes(
            @PathVariable UUID id,
            @RequestBody DoctorNotesRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.saveDoctorNotes(id, resolveHospitalId(auth), req));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(@RequestParam UUID hospitalId) {
        return ResponseEntity.ok(service.getStats(hospitalId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Email from the JWT; HMS uses {firstName lastName} but labs auth carries email only. */
    private String resolveFullName(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) return "System";
        try {
            String token = (String) auth.getCredentials();
            String email = jwtUtil.getEmail(token);
            return email != null ? email : "System";
        } catch (Exception e) {
            return auth.getName();
        }
    }

    private UUID resolveHospitalId(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) {
            throw new RuntimeException("Hospital context missing on the current session");
        }
        UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
        if (hid == null) {
            throw new RuntimeException("Hospital context missing on the current session");
        }
        return hid;
    }
}
