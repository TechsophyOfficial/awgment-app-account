package com.techsophy.tsf.account.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Unforgeable service-account detector verifying both {@code client_id} and matching
 * allowlisted {@code azp} claims rather than forgeable {@code preferred_username} prefixes.
 */
@Component
public class ServiceAccountPrincipalDetector {

    private static final String CLAIM_CLIENT_ID = "client_id";
    private static final String CLAIM_AZP = "azp";

    private final Set<String> allowedServiceAccountClients;

    public ServiceAccountPrincipalDetector(AwgmentSecurityProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.allowedServiceAccountClients = Set.copyOf(
                Objects.requireNonNullElse(properties.getServiceAccountClients(), java.util.List.of())
        );
    }

    public boolean isVerifiedServiceAccount(Jwt jwt) {
        return verifiedClientId(jwt).isPresent();
    }

    public Optional<String> verifiedClientId(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        Object rawClientId = jwt.getClaims().get(CLAIM_CLIENT_ID);
        Object rawAzp = jwt.getClaims().get(CLAIM_AZP);
        if (!(rawClientId instanceof String clientId) || clientId.isBlank()) {
            return Optional.empty();
        }
        if (!(rawAzp instanceof String azp) || azp.isBlank()) {
            return Optional.empty();
        }
        if (!clientId.equals(azp) || !allowedServiceAccountClients.contains(azp)) {
            return Optional.empty();
        }
        return Optional.of(clientId);
    }
}
