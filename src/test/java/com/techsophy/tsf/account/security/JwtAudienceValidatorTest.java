package com.techsophy.tsf.account.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAudienceValidatorTest {

    private final JwtAudienceValidator validator = new JwtAudienceValidator("awgment-account");

    @Test
    void constructorShouldRejectNullOrBlankAudience() {
        assertThatThrownBy(() -> new JwtAudienceValidator(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtAudienceValidator(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtAudienceValidator("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptJwtWhenExpectedAudienceIsPresentInListOrSingleString() {
        Jwt listMatch = jwtWithAud(List.of("web-ui", "awgment-account"));
        assertThat(validator.validate(listMatch).hasErrors()).isFalse();

        Jwt singleStringMatch = jwtWithAud("awgment-account");
        assertThat(validator.validate(singleStringMatch).hasErrors()).isFalse();

        JwtAudienceValidator trimmedValidator = new JwtAudienceValidator("  awgment-account  ");
        assertThat(trimmedValidator.validate(listMatch).hasErrors()).isFalse();
    }

    @Test
    void shouldRejectNullJwtMissingAudienceEmptyAudienceOrMismatchedAudience() {
        assertInvalidToken(validator.validate(null));

        Jwt noAud = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "user-1")
                .build();
        assertInvalidToken(validator.validate(noAud));

        assertInvalidToken(validator.validate(jwtWithAud(List.of())));
        assertInvalidToken(validator.validate(jwtWithAud(List.of("other-client", "awgment-account-admin"))));
        assertInvalidToken(validator.validate(jwtWithAud("awgment-account,other")));
        assertInvalidToken(validator.validate(jwtWithAud(Arrays.asList(123, null, "other"))));
        assertInvalidToken(validator.validate(jwtWithAud(123)));
        assertInvalidToken(validator.validate(jwtWithAud(Map.of("aud", "awgment-account"))));
    }

    private static Jwt jwtWithAud(Object audClaim) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "user-1")
                .claim("aud", audClaim)
                .build();
    }

    private static void assertInvalidToken(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .contains("invalid_token");
    }
}
