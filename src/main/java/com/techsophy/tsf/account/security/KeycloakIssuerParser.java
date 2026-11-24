package com.techsophy.tsf.account.security;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Strictly validates and extracts Keycloak realm names from {@code iss} claims
 * against the configured normalized {@code keycloakBaseUrl} without triggering
 * outbound HTTP requests or allowing path-traversal, query, fragment, or userinfo SSRF tricks.
 */
final class KeycloakIssuerParser {

    private static final Pattern REALM_SEGMENT_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");

    private final String expectedRealmPrefix;

    KeycloakIssuerParser(AwgmentSecurityProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String baseUrl = properties.normalizedKeycloakBaseUrl();
        if ((!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))
                || baseUrl.indexOf('@') >= 0
                || baseUrl.indexOf('?') >= 0
                || baseUrl.indexOf('#') >= 0) {
            throw new IllegalArgumentException("awgment.security.keycloak-base-url must be a clean http/https base URL");
        }
        this.expectedRealmPrefix = baseUrl + "/realms/";
    }

    Optional<String> extractRealm(String issuer) {
        if (issuer == null || issuer.isBlank() || !issuer.startsWith(expectedRealmPrefix)) {
            return Optional.empty();
        }
        String realmSegment = issuer.substring(expectedRealmPrefix.length());
        if (!REALM_SEGMENT_PATTERN.matcher(realmSegment).matches()) {
            return Optional.empty();
        }
        return Optional.of(realmSegment);
    }

    String issuerUriForRealm(String realm) {
        if (realm == null || !REALM_SEGMENT_PATTERN.matcher(realm).matches()) {
            throw new IllegalArgumentException("Invalid realm segment");
        }
        return expectedRealmPrefix + realm;
    }
}
