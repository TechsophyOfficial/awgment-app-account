package com.techsophy.tsf.account.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/**
 * Typed security configuration properties bound from {@code awgment.security.*}.
 */
@Validated
@ConfigurationProperties(prefix = "awgment.security")
public class AwgmentSecurityProperties {

    private String platformRealm = "platform";
    private String expectedAudience = "awgment-account";
    private String trustedClientId = "awgment-account";
    private String keycloakBaseUrl = "http://localhost:8080";
    private String keycloakInternalBaseUrl = "";
    private boolean devDocsEnabled = false;
    private List<String> serviceAccountClients = List.of("awgment-service-client");

    @Min(1)
    private int decoderCacheMaxSize = 500;

    @Min(0)
    private long decoderCacheTtlSeconds = 300L;

    @Min(100)
    private int oidcConnectTimeoutMs = 5000;

    @Min(100)
    private int oidcReadTimeoutMs = 8000;

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
        return stripTrailingSlashes(keycloakBaseUrl);
    }

    public String getKeycloakInternalBaseUrl() {
        return keycloakInternalBaseUrl;
    }

    public void setKeycloakInternalBaseUrl(String keycloakInternalBaseUrl) {
        this.keycloakInternalBaseUrl = (keycloakInternalBaseUrl == null || keycloakInternalBaseUrl.isBlank())
                ? ""
                : keycloakInternalBaseUrl.trim();
    }

    public String normalizedKeycloakInternalBaseUrl() {
        if (keycloakInternalBaseUrl == null || keycloakInternalBaseUrl.isBlank()) {
            return "";
        }
        return stripTrailingSlashes(keycloakInternalBaseUrl);
    }

    public int getDecoderCacheMaxSize() {
        return decoderCacheMaxSize;
    }

    public void setDecoderCacheMaxSize(int decoderCacheMaxSize) {
        this.decoderCacheMaxSize = decoderCacheMaxSize;
    }

    public long getDecoderCacheTtlSeconds() {
        return decoderCacheTtlSeconds;
    }

    public void setDecoderCacheTtlSeconds(long decoderCacheTtlSeconds) {
        this.decoderCacheTtlSeconds = decoderCacheTtlSeconds;
    }

    public int getOidcConnectTimeoutMs() {
        return oidcConnectTimeoutMs;
    }

    public void setOidcConnectTimeoutMs(int oidcConnectTimeoutMs) {
        this.oidcConnectTimeoutMs = oidcConnectTimeoutMs;
    }

    public int getOidcReadTimeoutMs() {
        return oidcReadTimeoutMs;
    }

    public void setOidcReadTimeoutMs(int oidcReadTimeoutMs) {
        this.oidcReadTimeoutMs = oidcReadTimeoutMs;
    }

    private static String stripTrailingSlashes(String raw) {
        String base = raw;
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
