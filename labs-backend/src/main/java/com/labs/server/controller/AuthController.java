package com.labs.server.controller;

import com.labs.server.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    @Value("${sso.cookie.name:sso_token}")
    private String cookieName;

    @Value("${sso.cookie.domain:zenohosp.com}")
    private String cookieDomain;

    @GetMapping("/user/me")
    public Map<String, Object> me(Authentication authentication) {
        String token = (String) authentication.getCredentials();

        return Map.of("data", Map.of(
            "userId", jwtUtil.getUserId(token).toString(),
            "email", jwtUtil.getEmail(token),
            "role", jwtUtil.getRole(token),
            "hospitalId", jwtUtil.getHospitalId(token) != null
                ? jwtUtil.getHospitalId(token).toString()
                : "",
            "firstName", jwtUtil.getStringClaim(token, "firstName"),
            "lastName", jwtUtil.getStringClaim(token, "lastName"),
            "hospitalName", jwtUtil.getStringClaim(token, "hospitalName"),
            "modules", jwtUtil.getModules(token) != null
                ? jwtUtil.getModules(token)
                : java.util.List.of()));
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        String domain = cookieDomain != null && cookieDomain.startsWith(".") ? cookieDomain.substring(1) : cookieDomain;
        response.addHeader("Set-Cookie", String.format(
            "%s=; Domain=.%s; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=None",
            cookieName, domain
        ));
        return Map.of("message", "Logged out successfully", "status", "success");
    }
}
