package com.techsophy.tsf.account.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter("awgment-account");

    @Test
    void constructorsShouldValidateInputs() {
        assertThatThrownBy(() -> new JwtRoleConverter((AwgmentSecurityProperties) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JwtRoleConverter((String) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtRoleConverter("  "))
                .isInstanceOf(IllegalArgumentException.class);

        AwgmentSecurityProperties props = new AwgmentSecurityProperties();
        props.setTrustedClientId("custom-client");
        JwtRoleConverter fromProps = new JwtRoleConverter(props);
        Jwt jwt = jwtWithClaims(Map.of("resource_access", Map.of("custom-client", Map.of("roles", List.of("role-a")))));
        assertThat(fromProps.convert(jwt)).extracting(GrantedAuthority::getAuthority).containsExactly("role-a");
    }

    @Test
    void shouldExtractOnlyTrustedClientRolesAndIgnoreRealmAccessAndOtherClients() {
        Jwt jwt = jwtWithClaims(Map.of(
                "realm_access", Map.of("roles", List.of("awgment-platform-admin", "uma_authorization")),
                "resource_access", Map.of(
                        "awgment-account", Map.of("roles", Arrays.asList(
                                "awgment-account-admin", "awgment-user", "awgment-account-admin", null, "  ", 42)),
                        "awgment-app-client", Map.of("roles", List.of("awgment-platform-admin", "editor")),
                        "account", Map.of("roles", List.of("manage-account"))
                )
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("awgment-account-admin", "awgment-user");
    }

    @Test
    void shouldHandleNullJwtAndMalformedNonMapOrNonCollectionClaimsSafely() {
        assertThat(converter.convert(null)).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of()))).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of("resource_access", "not-a-map")))).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of("resource_access", List.of("awgment-account"))))).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of(
                "resource_access", Map.of("awgment-account", "not-a-map"))))).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of(
                "resource_access", Map.of("awgment-account", Map.of("roles", "not-a-list")))))).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of(
                "resource_access", Map.of("awgment-account", Map.of()))))).isEmpty();
    }

    private static Jwt jwtWithClaims(Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "user-1");
        extraClaims.forEach(builder::claim);
        return builder.build();
    }
}
