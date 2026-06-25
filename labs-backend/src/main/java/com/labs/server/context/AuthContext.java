package com.labs.server.context;

import com.labs.server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

@Component
@RequestScope
@RequiredArgsConstructor
public class AuthContext {

    private final JwtUtil jwtUtil;

    public UUID getHospitalId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getCredentials() == null) return null;
        return jwtUtil.getHospitalId((String) auth.getCredentials());
    }

    public String getUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? (String) auth.getPrincipal() : null;
    }

    public String getEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getCredentials() == null) return null;
        return jwtUtil.getEmail((String) auth.getCredentials());
    }

    public String getRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getCredentials() == null) return null;
        return jwtUtil.getRole((String) auth.getCredentials());
    }
}
