package com.techsophy.tsf.account.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts Awgment functional authorities strictly from {@code resource_access.<trustedClientId>.roles}.
 * Intentionally ignores {@code realm_access} and all untrusted client entries.
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String trustedClientId;

    @Autowired
    public JwtRoleConverter(AwgmentSecurityProperties properties) {
        this(Objects.requireNonNull(properties, "properties must not be null").getTrustedClientId());
    }

    public JwtRoleConverter(String trustedClientId) {
        if (trustedClientId == null || trustedClientId.isBlank()) {
            throw new IllegalArgumentException("trustedClientId must not be blank");
        }
        this.trustedClientId = trustedClientId.trim();
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Object resourceAccessObj = jwt.getClaims().get("resource_access");
        if (!(resourceAccessObj instanceof Map<?, ?> resourceAccessMap)) {
            return List.of();
        }
        Object clientAccessObj = resourceAccessMap.get(trustedClientId);
        if (!(clientAccessObj instanceof Map<?, ?> clientAccessMap)) {
            return List.of();
        }
        Object rolesObj = clientAccessMap.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return List.of();
        }
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Object roleObj : roles) {
            if (roleObj instanceof String role && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
        }
        return List.copyOf(authorities);
    }
}
