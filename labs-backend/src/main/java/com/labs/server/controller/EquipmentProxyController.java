package com.labs.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.labs.server.security.JwtUtil;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only "equipment used" picker for result entry. Two source-of-truth
 * calls, composed here (neither service owns the other's data):
 *
 *  1. asset-manager  GET /api/assets       — this hospital's non-disposed assets
 *                                             (already hospital-scoped via JWT)
 *  2. HMS            GET /api/rooms        — this hospital's rooms, each carrying
 *                                             roomCategory resolved from room_type_configs
 *
 * An asset can sit in ANY room (a patient ward, ICU, OT — not just a lab), so
 * listing every hospital asset would surface irrelevant equipment (e.g. a
 * nurse station's vitals monitor) next to real lab machines. We keep only
 * assets whose room has {@code roomCategory == "LAB"} (Pathology Lab, X-Ray,
 * CT, MRI, USG, ECG/Echo, Mammography, DEXA, Sample Collection — see HMS
 * room_type_configs). BLOOD_BANK is deliberately excluded for now — pending
 * discussion on whether blood-bank analyzers belong in this picker.
 *
 * Safety fallback: if the HMS rooms call fails or errors (e.g. a caller role
 * HMS's /api/rooms doesn't allow — see class-level note below), we fall back
 * to the UNFILTERED asset list rather than showing an empty picker. A broken
 * cross-service call should degrade the feature, not block result entry.
 *
 * Known gap: HMS's RoomController restricts GET /api/rooms to
 * hasAnyRole('hospital_admin','doctor','staff') — the 'technician' role
 * (likely what lab techs carry) is not in that list, so real lab-tech
 * sessions will hit the fallback path today. Flagged for a follow-up HMS
 * change; not fixed here since it's HMS's security policy to own.
 */
@Slf4j
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentProxyController {

    private static final String SSO_COOKIE_FALLBACK = "sso_token";
    private static final String LAB_ROOM_CATEGORY = "LAB";

    private final RestTemplate proxyRestTemplate;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${asset.api.url}")
    private String assetApiUrl;

    @Value("${hms.api.url}")
    private String hmsApiUrl;

    @Value("${sso.cookie.name:sso_token}")
    private String ssoCookieName;

    @GetMapping
    public ResponseEntity<byte[]> list(HttpServletRequest request) {
        String token = extractToken(request);
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        byte[] assetsBody = fetchAssets(entity);
        if (assetsBody == null) {
            return ResponseEntity.status(502).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Bad Gateway\",\"message\":\"Assets service unreachable\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        Set<Long> labRoomIds = fetchLabRoomIds(entity, token);
        if (labRoomIds == null) {
            // HMS unreachable or forbidden for this caller — degrade to unfiltered
            // rather than leaving the picker empty.
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(assetsBody);
        }

        try {
            ArrayNode assets = (ArrayNode) objectMapper.readTree(assetsBody);
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode asset : assets) {
                JsonNode roomId = asset.get("roomId");
                if (roomId != null && !roomId.isNull() && labRoomIds.contains(roomId.asLong())) {
                    filtered.add(asset);
                }
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsBytes(filtered));
        } catch (Exception e) {
            log.warn("Failed to filter assets by lab room — returning unfiltered: {}", e.getMessage());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(assetsBody);
        }
    }

    private byte[] fetchAssets(HttpEntity<Void> entity) {
        URI target = UriComponentsBuilder.fromHttpUrl(assetApiUrl).path("/api/assets").build(true).toUri();
        try {
            ResponseEntity<byte[]> resp = proxyRestTemplate.exchange(target, HttpMethod.GET, entity, byte[].class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("Assets service returned {} for {}", resp.getStatusCode(), target);
                return null;
            }
            return resp.getBody();
        } catch (ResourceAccessException e) {
            log.warn("Assets service unreachable for GET {}: {}", target, e.getMessage());
            return null;
        }
    }

    /** Returns null (not empty set) on any failure — callers must treat null as "skip filtering". */
    private Set<Long> fetchLabRoomIds(HttpEntity<Void> entity, String token) {
        if (token == null || token.isBlank()) return null;
        UUID hospitalId;
        try {
            hospitalId = jwtUtil.getHospitalId(token);
        } catch (Exception e) {
            return null;
        }
        if (hospitalId == null) return null;

        URI target = UriComponentsBuilder.fromHttpUrl(hmsApiUrl)
                .path("/api/rooms")
                .queryParam("hospitalId", hospitalId)
                .build(true).toUri();
        try {
            ResponseEntity<byte[]> resp = proxyRestTemplate.exchange(target, HttpMethod.GET, entity, byte[].class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("HMS rooms lookup returned {} for {} — skipping lab-room filter", resp.getStatusCode(), target);
                return null;
            }
            ArrayNode rooms = (ArrayNode) objectMapper.readTree(resp.getBody());
            Set<Long> ids = new HashSet<>();
            for (JsonNode room : rooms) {
                JsonNode category = room.get("roomCategory");
                JsonNode id = room.get("id");
                if (category != null && LAB_ROOM_CATEGORY.equals(category.asText()) && id != null && !id.isNull()) {
                    ids.add(id.asLong());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("HMS rooms lookup failed for {} — skipping lab-room filter: {}", target, e.getMessage());
            return null;
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
