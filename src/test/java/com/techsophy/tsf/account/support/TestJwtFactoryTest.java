package com.techsophy.tsf.account.support;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestJwtFactoryTest {

    private final TestJwtFactory factory = new TestJwtFactory();

    @Test
    void shouldMintAndDecodeActiveHumanJwtWithScopedRoles() {
        String token = factory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "sub-1", "acme-operator", List.of("awgment-account-all")));

        Jwt jwt = factory.decode(token);

        assertThat(jwt.getIssuer().toString()).isEqualTo("http://localhost:8080/realms/acme");
        assertThat(jwt.getSubject()).isEqualTo("sub-1");
        assertThat(jwt.getAudience()).containsExactly("awgment-account");
        assertThat(jwt.getClaimAsString("awgment_status")).isEqualTo("ACTIVE");
        assertThat(jwt.getClaimAsString("client_id")).isNull();
        assertThat(jwt.<Map<String, Object>>getClaim("resource_access")).containsKey("awgment-account");
    }

    @Test
    void shouldMintServiceAccountJwtWithUnforgeableClientIdAndTokenKind() {
        String token = factory.mintToken(TestJwtFactory.TokenSpec.serviceAccount(
                "acme", "awgment-service-client", List.of("awgment-system-service")));

        Jwt jwt = factory.decode(token);

        assertThat(jwt.getClaimAsString("client_id")).isEqualTo("awgment-service-client");
        assertThat(jwt.getClaimAsString("azp")).isEqualTo("awgment-service-client");
        assertThat(jwt.getClaimAsString("token_kind")).isEqualTo("service_account");
    }

    @Test
    void shouldMintExternalIdpJwtWithVerifiedEmailAndTenantClaim() {
        TestJwtFactory.TokenSpec spec = new TestJwtFactory.TokenSpec(
                "http://localhost:8080/realms/acme",
                "sub-entra-1",
                List.of("awgment-account"),
                "awgment-web",
                "corp-employee",
                "corp.employee.test",
                true,
                "entra-acme",
                "acme-entra-tenant-uuid",
                null,
                null,
                null,
                Map.of(),
                List.of("/engineering"),
                300
        );

        Jwt jwt = factory.decode(factory.mintToken(spec));

        assertThat(jwt.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(jwt.getClaimAsString("identity_provider")).isEqualTo("entra-acme");
        assertThat(jwt.getClaimAsString("idp_tenant_id")).isEqualTo("acme-entra-tenant-uuid");
        assertThat(jwt.getClaimAsStringList("groups")).containsExactly("/engineering");
        assertThat(jwt.getClaimAsString("awgment_status")).isNull();
    }
}
