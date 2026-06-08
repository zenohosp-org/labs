package com.labs.server.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;

import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtOAuth2UserService extends DefaultOAuth2UserService {

    private final JwtUtil jwtUtil;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String tokenValue = userRequest.getAccessToken().getTokenValue();

        try {
            Claims claims = jwtUtil.parseToken(tokenValue);

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", claims.getSubject());
            attributes.put("email", claims.get("email", String.class));
            attributes.put("role", claims.get("role", String.class));

            String role = claims.get("role", String.class);
            Collection<SimpleGrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    : Collections.emptyList();

            attributes.put("access_token", tokenValue);

            String hospitalId = claims.get("hospitalId", String.class);
            if (hospitalId != null) {
                attributes.put("hospitalId", hospitalId);
            }

            return new DefaultOAuth2User(
                    authorities,
                    attributes,
                    "sub"
            );
        } catch (Exception e) {
            throw new OAuth2AuthenticationException("Failed to parse JWT access token: " + e.getMessage());
        }
    }
}
