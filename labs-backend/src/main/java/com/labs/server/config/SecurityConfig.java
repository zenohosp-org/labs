package com.labs.server.config;

import com.labs.server.security.JwtFilter;
import com.labs.server.security.JwtOAuth2UserService;
import com.labs.server.security.OAuth2SsoSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final OAuth2SsoSuccessHandler ssoSuccessHandler;
    private final JwtOAuth2UserService jwtOAuth2UserService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())

            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()
                // /api/health is an open liveness/diagnostic endpoint — needed
                // when the container is up but a downstream config is missing
                // (e.g. HMS_API_URL), so ops can curl it without a JWT.
                .requestMatchers("/api/health").permitAll()
                // Phase 5 — public report verification (QR target). Returns a
                // minimal payload (initials + signatory + signed_at + accession);
                // no PHI beyond what the patient already physically holds.
                .requestMatchers("/api/report-verify/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())

            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(jwtOAuth2UserService))
                .successHandler(ssoSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    log.error("SSO Login Failure: {}", exception.getMessage(), exception);
                    response.sendRedirect(frontendUrl + "/login?error=sso_failed");
                }))

            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(
                            "{\"error\": \"Unauthorized\", \"message\": \"Authentication required\"}");
                    } else {
                        response.sendRedirect("/oauth2/authorization/directory");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.error("Access denied to {}: {}", request.getRequestURI(),
                        accessDeniedException.getMessage());
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\": \"Forbidden\", \"message\": \"You do not have permission to access this resource\"}");
                }));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
            List.of(
                "http://localhost:5173", "http://localhost:5174", "http://localhost:5175",
                "https://labs.zenohosp.com",
                "https://pharmacy.zenohosp.com",
                "https://directory.zenohosp.com",
                "https://hms.zenohosp.com"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
