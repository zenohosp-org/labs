package com.labs.server.controller;

import com.labs.server.security.JwtUtil;
import com.labs.server.service.LabService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
public class LabsController {

    private final LabService labService;
    private final JwtUtil jwtUtil;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Welcome to ZenoLabs");
        result.put("pendingCollection", 0L);
        result.put("awaitingReport", 0L);
        result.put("completedToday", 0L);

        if (authentication != null && authentication.getCredentials() != null) {
            try {
                String token = (String) authentication.getCredentials();
                UUID hospitalId = jwtUtil.getHospitalId(token);
                if (hospitalId != null) {
                    result.put("pendingCollection",
                            labService.countByStatus(hospitalId, "PENDING_COLLECTION"));
                    result.put("awaitingReport",
                            labService.countByStatus(hospitalId, "AWAITING_REPORT"));
                    result.put("completedToday", labService.countCompletedReports(hospitalId));
                }
            } catch (Exception ignored) {
                // Token may be malformed during early SSO; defaults are returned.
            }
        }
        return result;
    }
}
