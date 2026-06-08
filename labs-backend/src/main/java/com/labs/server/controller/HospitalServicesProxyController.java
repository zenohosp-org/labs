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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Pass-through proxy for {@code /api/hospital-services}. Forwards each call
 * to HMS at {@link #hmsApiUrl} with the caller's {@code Authorization} header
 * preserved, and returns HMS's status + body byte-for-byte so the labs
 * frontend sees the same response shape it would get hitting HMS directly.
 *
 * <p>Labs no longer maps {@code hospital_services} as a JPA entity — HMS owns
 * the table and the CRUD lifecycle. Labs only exists in the request path so
 * the frontend keeps calling its own backend.
 *
 * <p>Errors from HMS (4xx/5xx) propagate through with the same status code
 * and body. Network failures surface as {@code 502 Bad Gateway}.
 */
@Slf4j
@RestController
@RequestMapping("/api/hospital-services")
@RequiredArgsConstructor
public class HospitalServicesProxyController {

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
        return forward(HttpMethod.GET, buildUri("", query), null, request);
    }

    @PostMapping
    public ResponseEntity<byte[]> create(
            @RequestBody byte[] body,
            HttpServletRequest request) {
        return forward(HttpMethod.POST, buildUri("", Map.of()), body, request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<byte[]> update(
            @PathVariable UUID id,
            @RequestBody byte[] body,
            HttpServletRequest request) {
        return forward(HttpMethod.PUT, buildUri("/" + id, Map.of()), body, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<byte[]> delete(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return forward(HttpMethod.DELETE, buildUri("/" + id, Map.of()), null, request);
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<byte[]> toggleStatus(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return forward(HttpMethod.PATCH, buildUri("/" + id + "/toggle-status", Map.of()), null, request);
    }

    // ── Plumbing ──────────────────────────────────────────────────────────

    private URI buildUri(String suffix, Map<String, String> queryParams) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(hmsApiUrl)
                .path("/api/hospital-services")
                .path(suffix);
        queryParams.forEach(b::queryParam);
        return b.build(true).toUri();
    }

    /**
     * Forwards the request to HMS, preserving the caller's auth + content type
     * and returning HMS's status + body verbatim. Pass-through error model:
     * any HTTP response (incl. 4xx / 5xx) is returned to the caller; only
     * network failures surface as 502.
     */
    private ResponseEntity<byte[]> forward(HttpMethod method, URI target, byte[] body, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String token = extractToken(request);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        String contentType = request.getContentType();
        headers.setContentType(contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_JSON);

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> resp = proxyRestTemplate.exchange(target, method, entity, byte[].class);
            HttpHeaders out = new HttpHeaders();
            MediaType ct = resp.getHeaders().getContentType();
            if (ct != null) out.setContentType(ct);
            return ResponseEntity.status(resp.getStatusCode()).headers(out).body(resp.getBody());
        } catch (ResourceAccessException e) {
            log.warn("HMS unreachable for {} {}: {}", method, target, e.getMessage());
            String body502 = "{\"error\":\"Bad Gateway\",\"message\":\"HMS service unreachable\"}";
            HttpHeaders out = new HttpHeaders();
            out.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(502).headers(out).body(body502.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Mirrors {@code JwtFilter.extractToken} — prefer Authorization header,
     * fall back to the shared SSO cookie. Same precedence labs uses itself
     * so the proxy never sees a request differently from the rest of the API.
     */
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
