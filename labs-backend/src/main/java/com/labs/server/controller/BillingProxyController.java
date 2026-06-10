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
 * Pass-through to HMS for the two billing surfaces labs needs but doesn't
 * own:
 * <ul>
 *   <li>{@code GET  /api/bank-accounts} — populates the bank-account dropdown
 *       on the labs Collect-Payment modal.</li>
 *   <li>{@code POST /api/billing/invoices/{invoiceId}/payments} — records an
 *       OPD payment against an invoice labs already created (auto-bill on
 *       report-gen). HMS owns InvoiceService.collectPayment.</li>
 * </ul>
 *
 * Same wire pattern as {@code HospitalServicesProxyController}: the caller's
 * JWT is forwarded; HMS's status + body are returned verbatim; network
 * failures surface as 502.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BillingProxyController {

    private static final String SSO_COOKIE_FALLBACK = "sso_token";

    private final RestTemplate proxyRestTemplate;

    @Value("${hms.api.url}")
    private String hmsApiUrl;

    @Value("${sso.cookie.name:sso_token}")
    private String ssoCookieName;

    @GetMapping("/api/bank-accounts")
    public ResponseEntity<byte[]> listBankAccounts(
            @RequestParam Map<String, String> query,
            HttpServletRequest request) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(hmsApiUrl).path("/api/bank-accounts");
        query.forEach(b::queryParam);
        return forward(HttpMethod.GET, b.build(true).toUri(), null, request);
    }

    @PostMapping("/api/billing/invoices/{invoiceId}/payments")
    public ResponseEntity<byte[]> collectPayment(
            @PathVariable UUID invoiceId,
            @RequestBody byte[] body,
            HttpServletRequest request) {
        URI target = UriComponentsBuilder.fromHttpUrl(hmsApiUrl)
                .path("/api/billing/invoices/" + invoiceId + "/payments")
                .build(true).toUri();
        return forward(HttpMethod.POST, target, body, request);
    }

    // ── Plumbing (identical to the other proxy controllers) ──────────────

    private ResponseEntity<byte[]> forward(HttpMethod method, URI target, byte[] body,
                                           HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String token = extractToken(request);
        if (token != null && !token.isBlank()) headers.setBearerAuth(token);
        String contentType = request.getContentType();
        headers.setContentType(contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<byte[]> resp = proxyRestTemplate.exchange(
                    target, method, new HttpEntity<>(body, headers), byte[].class);
            HttpHeaders out = new HttpHeaders();
            if (resp.getHeaders().getContentType() != null) {
                out.setContentType(resp.getHeaders().getContentType());
            }
            return ResponseEntity.status(resp.getStatusCode()).headers(out).body(resp.getBody());
        } catch (ResourceAccessException e) {
            log.warn("HMS unreachable for {} {}: {}", method, target, e.getMessage());
            HttpHeaders out = new HttpHeaders();
            out.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(502).headers(out).body(
                    "{\"error\":\"Bad Gateway\",\"message\":\"HMS service unreachable\"}"
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
