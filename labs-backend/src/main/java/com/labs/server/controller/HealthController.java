package com.labs.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Open liveness endpoint — no JWT required.
 *
 * Reports container health (Spring is up), DB reachability, and the resolved
 * downstream URLs. Lets ops diagnose "is labs alive?" without first solving
 * "is my JWT valid?" — which was the broken cycle in the last prod outage.
 *
 * Body is intentionally tiny so cold-start probes don't time out.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${hms.api.url}")
    private String hmsApiUrl;

    @Value("${directory.api.url}")
    private String directoryApiUrl;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("ts", Instant.now().toString());

        // DB ping. Cheap (single round-trip) and confirms the pool is live.
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            db.put("status", Integer.valueOf(1).equals(one) ? "UP" : "DEGRADED");
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        out.put("db", db);

        // Downstream URL snapshot — if these resolve to localhost in prod, the
        // env var wasn't set. The single most common labs prod issue.
        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("hmsApiUrl", hmsApiUrl);
        downstream.put("directoryApiUrl", directoryApiUrl);
        downstream.put("frontendUrl", frontendUrl);
        out.put("downstream", downstream);

        return out;
    }
}
