package com.labs.server.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Read-only pass-through proxy for {@code /api/equipment} → asset-manager's
 * {@code GET /api/assets}, which already resolves the caller's hospitalId
 * from the JWT and returns that hospital's non-disposed assets
 * ({@code findActiveByHospitalId}).
 *
 * asset-manager owns the {@code assets} table (same shared Supabase DB as
 * labs, but a different service's write path) — labs only lists rows here to
 * power the "equipment used" picker on result entry
 * ({@link com.labs.server.entity.LabTestResult#getInstrumentId()}). No
 * create/update/delete: asset lifecycle stays in the Assets app.
 *
 * Mirrors {@link HospitalServicesProxyController}'s forward-verbatim pattern.
 */
@Slf4j
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentProxyController {

    private static final String SSO_COOKIE_FALLBACK = "sso_token";

    private final RestTemplate proxyRestTemplate;

    @Value("${asset.api.url}")
    private String assetApiUrl;

    @Value("${sso.cookie.name:sso_token}")
    private String ssoCookieName;

    @GetMapping
    public ResponseEntity<byte[]> list(HttpServletRequest request) {
        URI target = UriComponentsBuilder
                .fromHttpUrl(assetApiUrl)
                .path("/api/assets")
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        String token = extractToken(request);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<byte[]> resp = proxyRestTemplate.exchange(
                    target, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            HttpHeaders out = new HttpHeaders();
            MediaType ct = resp.getHeaders().getContentType();
            if (ct != null) out.setContentType(ct);
            return ResponseEntity.status(resp.getStatusCode()).headers(out).body(resp.getBody());
        } catch (ResourceAccessException e) {
            log.warn("Assets service unreachable for GET {}: {}", target, e.getMessage());
            String body502 = "{\"error\":\"Bad Gateway\",\"message\":\"Assets service unreachable\"}";
            HttpHeaders out = new HttpHeaders();
            out.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(502).headers(out).body(body502.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Mirrors {@code JwtFilter.extractToken} / {@link HospitalServicesProxyController#extractToken}. */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            String name = ssoCookieName != null && !ssoCookieName.isBlank()
                    ? ssoCookieName : SSO_COOKIE_FALLBACK;
            for (Cookie c : request.getCookies()) {
                if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
