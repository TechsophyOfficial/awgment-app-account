package com.techsophy.tsf.account.security;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Default {@link JwtDecoderFactory} backed by {@link NimbusJwtDecoder#withIssuerLocation(String)}
 * enforcing bounded discovery/JWKS HTTP timeouts, issuer validation, timestamp/nbf checks,
 * and explicit audience validation.
 */
@Component
public class NimbusJwtDecoderFactory implements JwtDecoderFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestTemplate restTemplate;

    public NimbusJwtDecoderFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri)
                .restOperations(restTemplate)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new JwtAudienceValidator(expectedAudience)
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
