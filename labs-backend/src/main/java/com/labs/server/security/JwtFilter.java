package com.labs.server.security;

import com.labs.server.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${sso.cookie.name:sso_token}")
    private String cookieName;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        SecurityContextHolder.clearContext();

        String token = extractToken(request);

        if (token != null) {
            if (jwtUtil.isValid(token)) {
                try {
                    UUID uid = jwtUtil.getUserId(token);
                    // Tighten to match HMS posture: a signature-valid JWT alone is
                    // not enough — the user must still exist and be active. Closes
                    // the leaked-secret / disabled-user gap labs had vs HMS.
                    if (userRepository.findByIdAndIsActiveTrue(uid).isPresent()) {
                        String userId = uid.toString();
                        String role = jwtUtil.getRole(token);

                        if (role == null)
                            role = "USER";

                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                        var auth = new UsernamePasswordAuthenticationToken(userId, token, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        logger.debug("JWT signature valid but user " + uid + " missing or inactive — request stays anonymous");
                    }
                } catch (Exception e) {
                    logger.error("Failed to set security context from token: " + e.getMessage(), e);
                }
            } else {
                logger.debug("Invalid JWT token for request to " + request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (token != null && !token.isEmpty()) {
                        logger.debug("Token extracted from cookie: " + cookieName);
                        return token;
                    }
                }
            }
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            logger.debug("Token extracted from Authorization header");
            return header.substring(7);
        }

        return null;
    }
}
