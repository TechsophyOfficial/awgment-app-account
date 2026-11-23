package com.techsophy.tsf.account.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * Typed security configuration properties bound from {@code awgment.security.*}.
 */
@ConfigurationProperties(prefix = "awgment.security")
public class AwgmentSecurityProperties {

    private String platformRealm = "platform";
    private String expectedAudience = "awgment-account";
    private String trustedClientId = "awgment-account";
    private String keycloakBaseUrl = "http://localhost:8080";
    private boolean devDocsEnabled = false;
    private List<String> serviceAccountClients = List.of("awgment-service-client");

    public String getPlatformRealm() {
        return platformRealm;
    }

    public void setPlatformRealm(String platformRealm) {
        this.platformRealm = (platformRealm == null || platformRealm.isBlank())
                ? "platform"
                : platformRealm.trim();
    }

    public String getExpectedAudience() {
        return expectedAudience;
    }

    public void setExpectedAudience(String expectedAudience) {
        this.expectedAudience = (expectedAudience == null || expectedAudience.isBlank())
                ? "awgment-account"
                : expectedAudience.trim();
    }

    public String getTrustedClientId() {
        return trustedClientId;
    }

    public void setTrustedClientId(String trustedClientId) {
        this.trustedClientId = (trustedClientId == null || trustedClientId.isBlank())
                ? "awgment-account"
                : trustedClientId.trim();
    }

    public String getKeycloakBaseUrl() {
        return keycloakBaseUrl;
    }

    public void setKeycloakBaseUrl(String keycloakBaseUrl) {
        this.keycloakBaseUrl = (keycloakBaseUrl == null || keycloakBaseUrl.isBlank())
                ? "http://localhost:8080"
                : keycloakBaseUrl.trim();
    }

    public String normalizedKeycloakBaseUrl() {
        String base = keycloakBaseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public boolean isDevDocsEnabled() {
        return devDocsEnabled;
    }

    public void setDevDocsEnabled(boolean devDocsEnabled) {
        this.devDocsEnabled = devDocsEnabled;
    }

    public List<String> getServiceAccountClients() {
        return serviceAccountClients;
    }

    public void setServiceAccountClients(List<String> serviceAccountClients) {
        if (serviceAccountClients == null || serviceAccountClients.isEmpty()) {
            this.serviceAccountClients = List.of();
            return;
        }
        this.serviceAccountClients = serviceAccountClients.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(client -> !client.isBlank())
                .distinct()
                .toList();
    }
}
