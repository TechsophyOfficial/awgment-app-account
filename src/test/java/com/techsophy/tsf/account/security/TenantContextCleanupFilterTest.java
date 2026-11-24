package com.techsophy.tsf.account.security;

import com.techsophy.tsf.account.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextCleanupFilterTest {

    private final AwgmentSecurityProperties properties = new AwgmentSecurityProperties();
    private final TenantContextCleanupFilter filter = new TenantContextCleanupFilter(properties);

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void constructorShouldRejectNullProperties() {
        assertThatThrownBy(() -> new TenantContextCleanupFilter(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldBindValidTenantRealmDuringFilterExecutionAndClearAfterwards() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuer("http://localhost:8080/realms/acme")
                .subject("sub-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("awgment-user"))));

        AtomicReference<String> insideTenant = new AtomicReference<>();
        AtomicReference<String> insideSchema = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            insideTenant.set(TenantContext.getTenantId());
            insideSchema.set(TenantContext.getCurrentSchema());
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/accounts/v2/users/me"),
                new MockHttpServletResponse(), chain);

        assertThat(insideTenant.get()).isEqualTo("acme");
        assertThat(insideSchema.get()).isEqualTo("tenant_acme");
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void shouldBindStringIssClaimWhenIssuerUrlFieldIsAbsent() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", "http://localhost:8080/realms/beta_corp")
                .subject("sub-2")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        AtomicReference<String> insideTenant = new AtomicReference<>();
        filter.doFilter(new MockHttpServletRequest("GET", "/accounts/v2/users/me"),
                new MockHttpServletResponse(), (req, res) -> insideTenant.set(TenantContext.getTenantId()));

        assertThat(insideTenant.get()).isEqualTo("beta_corp");
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void shouldLeaveDefaultContextForPlatformJwtNonJwtAndInvalidOrMissingIssuer() throws Exception {
        // Stale pre-existing ThreadLocal must always be cleared before/after chain runs
        TenantContext.setTenantId("stale_tenant");

        // 1. Platform JWT
        Jwt platformJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuer("http://localhost:8080/realms/platform")
                .subject("platform-admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(platformJwt, List.of()));
        assertDefaultInsideAndAfter();

        // 2. Non-JWT @WithMockUser style authentication
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mock-admin", "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertDefaultInsideAndAfter();

        // 3. Unauthenticated (null Authentication)
        SecurityContextHolder.clearContext();
        assertDefaultInsideAndAfter();

        // 4. JWT missing iss or containing SSRF / reserved realm
        Jwt noIssJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject("sub-no-iss")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(noIssJwt, List.of()));
        assertDefaultInsideAndAfter();

        Jwt reservedJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuer("http://localhost:8080/realms/master")
                .subject("sub-master")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(reservedJwt, List.of()));
        assertDefaultInsideAndAfter();
    }

    @Test
    void shouldClearTenantContextInFinallyEvenWhenDownstreamThrowsRuntimeException() {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuer("http://localhost:8080/realms/acme")
                .subject("sub-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        FilterChain failingChain = (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("acme");
            throw new IllegalStateException("downstream failure");
        };

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("GET", "/accounts/v2/users/me"),
                new MockHttpServletResponse(),
                failingChain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    private void assertDefaultInsideAndAfter() throws Exception {
        AtomicReference<String> insideTenant = new AtomicReference<>();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/accounts/v2/tenants"),
                new MockHttpServletResponse(),
                (req, res) -> insideTenant.set(TenantContext.getTenantId()));
        assertThat(insideTenant.get()).isEqualTo(TenantContext.DEFAULT_TENANT);
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }
}
