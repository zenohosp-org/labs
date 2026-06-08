package com.labs.server.security;

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

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

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
                    String userId = jwtUtil.getUserId(token).toString();
                    String role = jwtUtil.getRole(token);

                    if (role == null)
                        role = "USER";

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    var auth = new UsernamePasswordAuthenticationToken(userId, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
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
