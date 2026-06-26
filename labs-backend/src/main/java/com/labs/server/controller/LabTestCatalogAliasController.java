package com.labs.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Phase 7 — redirect alias from the old {@code /api/lab-test-catalog/*}
 * path to the renamed {@code /api/lab-services/*}.
 *
 * Kept for one release so:
 *   - bookmarked admin URLs survive,
 *   - any external Postman / ops dashboard that still hits the old path
 *     gets a clean 308 with the new Location header instead of a 404,
 *   - we can see in the logs who hasn't migrated yet (WARN with caller IP).
 *
 * Drop in Phase 8 once metrics confirm zero traffic on the old path.
 */
@Slf4j
@RestController
@RequestMapping("/api/lab-test-catalog")
public class LabTestCatalogAliasController {

    @RequestMapping("/**")
    public ResponseEntity<Void> redirect(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String original = req.getRequestURI();
        String target = original.replaceFirst("^/api/lab-test-catalog", "/api/lab-services");
        String qs = req.getQueryString();
        if (qs != null && !qs.isBlank()) target = target + "?" + qs;

        String caller = req.getHeader("X-Forwarded-For");
        if (caller == null || caller.isBlank()) caller = req.getRemoteAddr();
        log.warn("DEPRECATED labs path {} {} from {} — issuing 308 to {}",
                req.getMethod(), original, caller, target);

        // 308 = Permanent Redirect, preserves method + body (vs 301/302 which
        // can downgrade POST → GET on some clients).
        res.setHeader("Location", target);
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).build();
    }
}
