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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Read-only pass-through for {@code GET /api/specializations}. HMS owns
 * specializations; labs only needs to surface them in the Services-edit
 * modal. Same wire pattern as {@link HospitalServicesProxyController}:
 * forward the caller's JWT, return HMS's status + body verbatim.
 */
@Slf4j
@RestController
@RequestMapping("/api/specializations")
@RequiredArgsConstructor
public class SpecializationsProxyController {

    private static final String SSO_COOKIE_FALLBACK = "sso_token";

    private final RestTemplate proxyRestTemplate;

    @Value("${hms.api.url}")
    private String hmsApiUrl;

    @Value("${sso.cookie.name:sso_token}")
    private String ssoCookieName;

    @GetMapping
    public ResponseEntity<byte[]> list(
            @RequestParam Map<String, String> query,
            HttpServletRequest request) {

        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(hmsApiUrl)
                .path("/api/specializations");
        query.forEach(b::queryParam);
        URI target = b.build(true).toUri();

        HttpHeaders headers = new HttpHeaders();
        String token = extractToken(request);
        if (token != null && !token.isBlank()) headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<byte[]> resp = proxyRestTemplate.exchange(
                    target, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            HttpHeaders out = new HttpHeaders();
            if (resp.getHeaders().getContentType() != null) {
                out.setContentType(resp.getHeaders().getContentType());
            }
            return ResponseEntity.status(resp.getStatusCode()).headers(out).body(resp.getBody());
        } catch (ResourceAccessException e) {
            log.warn("HMS unreachable for GET {}: {}", target, e.getMessage());
            HttpHeaders out = new HttpHeaders();
            out.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(502).headers(out)
                    .body("{\"error\":\"Bad Gateway\",\"message\":\"HMS service unreachable\"}"
                            .getBytes(StandardCharsets.UTF_8));
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
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
