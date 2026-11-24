package com.techsophy.tsf.account.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * Default {@link JwtDecoderFactory} enforcing non-redirecting bounded HTTP timeouts,
 * optional split-horizon internal JWKS retrieval with strict external {@code iss} validation,
 * timestamp/nbf checks, and explicit audience validation.
 */
@Component
public class NimbusJwtDecoderFactory implements JwtDecoderFactory {

    private static final int MIN_TIMEOUT_MS = 100;
    private static final String REALMS_SEGMENT = "/realms/";
    private static final String JWKS_SUFFIX = "/protocol/openid-connect/certs";

    private final AwgmentSecurityProperties properties;
    private final RestTemplate restTemplate;

    public NimbusJwtDecoderFactory() {
        this(new AwgmentSecurityProperties());
    }

    @Autowired
    public NimbusJwtDecoderFactory(AwgmentSecurityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        Duration connectTimeout = Duration.ofMillis(Math.max(properties.getOidcConnectTimeoutMs(), MIN_TIMEOUT_MS));
        Duration readTimeout = Duration.ofMillis(Math.max(properties.getOidcReadTimeoutMs(), MIN_TIMEOUT_MS));
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public JwtDecoder create(String issuerUri, String expectedAudience) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("issuerUri must not be blank");
        }
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedAudience must not be blank");
        }

        NimbusJwtDecoder decoder = buildDecoder(issuerUri);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new JwtAudienceValidator(expectedAudience)
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private NimbusJwtDecoder buildDecoder(String issuerUri) {
        String internalBase = properties.normalizedKeycloakInternalBaseUrl();
        String externalRealmPrefix = properties.normalizedKeycloakBaseUrl() + REALMS_SEGMENT;
        if (!internalBase.isEmpty() && issuerUri.startsWith(externalRealmPrefix)) {
            String realm = issuerUri.substring(externalRealmPrefix.length());
            String jwkSetUri = internalBase + REALMS_SEGMENT + realm + JWKS_SUFFIX;
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                    .restOperations(restTemplate)
                    .build();
        }
        return NimbusJwtDecoder.withIssuerLocation(issuerUri)
                .restOperations(restTemplate)
                .build();
    }
}
