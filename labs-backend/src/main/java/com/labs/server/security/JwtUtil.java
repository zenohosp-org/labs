package com.labs.server.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public String getEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    public UUID getHospitalId(String token) {
        Object hid = parseToken(token).get("hospitalId");
        if (hid == null) return null;
        String s = hid.toString();
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            String padded = String.format("%012d", Long.parseLong(s));
            return UUID.fromString("00000000-0000-0000-0000-" + padded);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getModules(String token) {
        return parseToken(token).get("modules", List.class);
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
