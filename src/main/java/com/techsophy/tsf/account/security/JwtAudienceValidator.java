package com.techsophy.tsf.account.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.Collection;

/**
 * Validates that a decoded JWT explicitly includes the configured {@code expectedAudience}
 * in its {@code aud} claim without substring or comma-split ambiguity.
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;
    private final OAuth2Error missingAudienceError;

    public JwtAudienceValidator(String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedAudience must not be blank");
        }
        this.expectedAudience = expectedAudience.trim();
        this.missingAudienceError = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "The required audience '" + this.expectedAudience + "' is missing.",
                null
        );
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt == null) {
            return OAuth2TokenValidatorResult.failure(missingAudienceError);
        }
        Object rawAud = jwt.getClaims().get(JwtClaimNames.AUD);
        if (rawAud instanceof String singleAud) {
            return expectedAudience.equals(singleAud)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(missingAudienceError);
        }
        if (rawAud instanceof Collection<?> audiences) {
            for (Object entry : audiences) {
                if (entry instanceof String aud && expectedAudience.equals(aud)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
        }
        return OAuth2TokenValidatorResult.failure(missingAudienceError);
    }
}
