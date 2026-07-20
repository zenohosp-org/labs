package com.labs.server.controller;

import com.labs.server.dto.CreateLabServiceRequest;
import com.labs.server.dto.LabServiceDTO;
import com.labs.server.security.JwtUtil;
import com.labs.server.service.LabCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Per-hospital LOINC-coded labs service catalogue (the labs-side parallel
 * of HMS hospital_services, in the lab_services table). Lazy-seeded on
 * first GET with a curated Indian-lab default set (CBC, LFT, RFT, Lipid,
 * Thyroid, Diabetes, Urine — ~7 panels + ~40 analytes). Mirrors the
 * lazy-seed convention used by {@link LabReferenceRangeController}.
 *
 * Pre-Phase-7 path /api/lab-test-catalog is kept as a redirect alias for
 * one release cycle ({@link LabTestCatalogAliasController}).
 */
@RestController
@RequestMapping("/api/lab-services")
@RequiredArgsConstructor
public class LabServiceController {

    private final LabCatalogService service;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<LabServiceDTO>> list(
            @RequestParam(required = false) UUID hospitalId,
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly,
            Authentication auth) {
        return ResponseEntity.ok(service.list(resolveHospitalId(auth, hospitalId), activeOnly));
    }

    /** Expand a panel code (e.g. "CBC") to its child analytes. */
    @GetMapping("/panel/{panelCode}")
    public ResponseEntity<List<LabServiceDTO>> expand(
            @PathVariable String panelCode,
            @RequestParam(required = false) UUID hospitalId,
            Authentication auth) {
        return ResponseEntity.ok(service.expandPanel(resolveHospitalId(auth, hospitalId), panelCode));
    }

    /**
     * Phase 3 — fuzzy search for the package / range editor pickers.
     * Returns top {@code limit} active rows whose name / test_code / aliases
     * / LOINC contains the query.
     */
    @GetMapping("/search")
    public ResponseEntity<List<LabServiceDTO>> search(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false) UUID hospitalId,
            Authentication auth) {
        return ResponseEntity.ok(service.search(resolveHospitalId(auth, hospitalId), q, limit));
    }

    /**
     * Phase 11 — search the GLOBAL LOINC master catalog (not this hospital's
     * services). Backs the "Add from catalog" picker in Settings → Lab Services.
     * No hospital scoping: the catalog is shared. The picked row is then created
     * as a hospital-scoped service via POST (upsert) below.
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<LabServiceDTO>> catalog(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.searchCatalog(q, limit));
    }

    /** Phase 3 — ranges that belong to a specific catalogue row. */
    @GetMapping("/{id}/ranges")
    public ResponseEntity<List<com.labs.server.entity.LabReferenceRange>> ranges(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(service.rangesFor(resolveHospitalId(auth, null), id));
    }

    @PostMapping
    public ResponseEntity<LabServiceDTO> upsert(
            @RequestBody CreateLabServiceRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.upsert(resolveHospitalId(auth, null), req));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<LabServiceDTO> toggle(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.toggle(resolveHospitalId(auth, null), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(resolveHospitalId(auth, null), id);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveHospitalId(Authentication auth, UUID fallback) {
        if (auth != null && auth.getCredentials() != null) {
            try {
                UUID hid = jwtUtil.getHospitalId((String) auth.getCredentials());
                if (hid != null) return hid;
            } catch (Exception ignored) { /* fall through */ }
        }
        if (fallback != null) return fallback;
        throw new RuntimeException("Hospital context missing on the current session");
    }
}
