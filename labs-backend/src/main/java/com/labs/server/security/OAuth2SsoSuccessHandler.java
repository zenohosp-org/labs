package com.labs.server.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

/**
 * Gates SSO completion on the JWT's modules claim. Directory issues a token
 * carrying a {@code modules} array — labs access requires the lowercase token
 * {@code "labs"} (Directory normalises module keys to lowercase across all
 * apps: hms, ot, pharmacy, asset, labs, …).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SsoSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REQUIRED_MODULE = "labs";

    private final JwtUtil jwtUtil;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            log.warn("SSO success handler invoked with unexpected authentication type");
            response.sendRedirect(frontendUrl + "/login?error=sso_failed");
            return;
        }

        OAuth2User principal = oauthToken.getPrincipal();
        String accessToken = principal != null
                ? (String) principal.getAttributes().get("access_token")
                : null;

        if (accessToken == null) {
            log.warn("SSO success for {} but no access_token attribute — rejecting",
                    oauthToken.getName());
            response.sendRedirect(frontendUrl + "/login?error=sso_failed");
            return;
        }

        if (!hasLabsModule(accessToken)) {
            log.warn("SSO success for {} but user lacks 'labs' module — rejecting",
                    oauthToken.getName());
            response.sendRedirect(frontendUrl + "/login?error=no_labs_access");
            return;
        }

        log.info("SSO success for principal {}, redirecting to frontend", oauthToken.getName());
        response.sendRedirect(frontendUrl + "/sso/callback");
    }

    private boolean hasLabsModule(String token) {
        try {
            Collection<String> modules = jwtUtil.getModules(token);
            if (modules == null || modules.isEmpty()) return false;
            return modules.stream()
                    .filter(m -> m != null)
                    .map(m -> m.toLowerCase(Locale.ROOT))
                    .anyMatch(REQUIRED_MODULE::equals);
        } catch (Exception e) {
            log.warn("Failed to parse modules claim during SSO: {}", e.getMessage());
            return false;
        }
    }
}
