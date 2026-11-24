package com.techsophy.tsf.account.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Factory creating per-realm {@link JwtDecoder} instances validated for issuer and audience.
 */
public interface JwtDecoderFactory {

    JwtDecoder create(String issuerUri, String expectedAudience);
}
