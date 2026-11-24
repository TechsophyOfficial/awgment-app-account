package com.techsophy.tsf.account.security;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAccountPrincipalDetectorTest {

    private final AwgmentSecurityProperties properties = new AwgmentSecurityProperties();
    private final ServiceAccountPrincipalDetector detector = new ServiceAccountPrincipalDetector(properties);

    @Test
    void shouldRejectNullDependenciesAndNullJwt() {
        assertThatThrownBy(() -> new ServiceAccountPrincipalDetector(null))
                .isInstanceOf(NullPointerException.class);

        assertThat(detector.isVerifiedServiceAccount(null)).isFalse();
        assertThat(detector.verifiedClientId(null)).isEmpty();
    }

    @Test
    void shouldVerifyGenuineServiceAccountWithAllowlistedMatchingClientIdAndAzp() {
        Jwt svcJwt = jwt(Map.of(
                "sub", "svc-uuid-1",
                "client_id", "awgment-service-client",
                "azp", "awgment-service-client",
                "preferred_username", "service-account-awgment-service-client"
        ));

        assertThat(detector.isVerifiedServiceAccount(svcJwt)).isTrue();
        assertThat(detector.verifiedClientId(svcJwt)).contains("awgment-service-client");
    }

    @Test
    void shouldRejectHumanUserSpoofingServiceAccountUsernamePrefix() {
        Jwt humanSpoof = jwt(Map.of(
                "sub", "human-uuid-1",
                "azp", "awgment-web",
                "preferred_username", "service-account-awgment-service-client"
        ));

        assertThat(detector.isVerifiedServiceAccount(humanSpoof)).isFalse();
        assertThat(detector.verifiedClientId(humanSpoof)).isEmpty();
    }

    @Test
    void shouldRejectMismatchedAzpNonStringOrUnallowlistedClients() {
        Jwt mismatchedAzp = jwt(Map.of(
                "client_id", "awgment-service-client",
                "azp", "awgment-web",
                "preferred_username", "service-account-awgment-service-client"
        ));
        assertThat(detector.isVerifiedServiceAccount(mismatchedAzp)).isFalse();

        Jwt unallowlisted = jwt(Map.of(
                "client_id", "rogue-client",
                "azp", "rogue-client"
        ));
        assertThat(detector.isVerifiedServiceAccount(unallowlisted)).isFalse();

        Jwt blankClientId = jwt(Map.of("client_id", "   ", "azp", "awgment-service-client"));
        assertThat(detector.isVerifiedServiceAccount(blankClientId)).isFalse();

        Jwt blankAzp = jwt(Map.of("client_id", "awgment-service-client", "azp", " "));
        assertThat(detector.isVerifiedServiceAccount(blankAzp)).isFalse();

        Jwt missingAzp = jwt(Map.of("client_id", "awgment-service-client"));
        assertThat(detector.isVerifiedServiceAccount(missingAzp)).isFalse();

        Jwt nonStringClaims = jwt(Map.of("client_id", 123, "azp", 123));
        assertThat(detector.isVerifiedServiceAccount(nonStringClaims)).isFalse();

        Jwt nonStringAzp = jwt(Map.of("client_id", "awgment-service-client", "azp", 123));
        assertThat(detector.isVerifiedServiceAccount(nonStringAzp)).isFalse();
    }

    @Test
    void shouldRejectAllWhenAllowlistIsEmptyOrNull() {
        AwgmentSecurityProperties emptyProps = new AwgmentSecurityProperties();
        emptyProps.setServiceAccountClients(List.of());
        ServiceAccountPrincipalDetector emptyDetector = new ServiceAccountPrincipalDetector(emptyProps);

        Jwt svcJwt = jwt(Map.of(
                "client_id", "awgment-service-client",
                "azp", "awgment-service-client"
        ));
        assertThat(emptyDetector.isVerifiedServiceAccount(svcJwt)).isFalse();

        AwgmentSecurityProperties nullProps = Mockito.mock(AwgmentSecurityProperties.class);
        Mockito.when(nullProps.getServiceAccountClients()).thenReturn(null);
        ServiceAccountPrincipalDetector nullListDetector = new ServiceAccountPrincipalDetector(nullProps);
        assertThat(nullListDetector.isVerifiedServiceAccount(svcJwt)).isFalse();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
