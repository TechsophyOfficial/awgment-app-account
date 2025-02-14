package com.techsophy.tsf.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWTRoleConverter extracts roles from a JWT token and converts them into Spring Security's GrantedAuthority.
 * This allows role-based authorization in the application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JWTRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final WebClientWrapper webClientWrapper;
    private final ObjectMapper objectMapper;
    private final TokenUtils tokenUtils;

    /**
     * Converts the JWT token into a collection of GrantedAuthority objects.
     *
     * @param jwt the JWT token containing role claims
     * @return a collection of GrantedAuthority extracted from the token
     */
    @SneakyThrows
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // Extract client roles from the JWT token
        List<String> awgmentRolesList = tokenUtils.getClientRoles(jwt.getTokenValue());

        // Convert role names into SimpleGrantedAuthority instances
        return awgmentRolesList.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
