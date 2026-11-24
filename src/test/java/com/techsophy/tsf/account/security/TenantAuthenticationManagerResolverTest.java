package com.techsophy.tsf.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthenticationManagerResolverTest {

    @Mock
    private TenantLookupService tenantLookupService;

    @Mock
    private JwtDecoderFactory jwtDecoderFactory;

    private AwgmentSecurityProperties properties;
    private TenantAuthenticationManagerResolver resolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new AwgmentSecurityProperties();
        resolver = new TenantAuthenticationManagerResolver(
                properties,
                tenantLookupService,
                jwtDecoderFactory,
                new JwtRoleConverter(properties),
                objectMapper
        );
    }

    @Test
    void constructorShouldRejectNullArguments() {
        JwtRoleConverter converter = new JwtRoleConverter(properties);
        assertThatThrownBy(() -> new TenantAuthenticationManagerResolver(
                null, tenantLookupService, jwtDecoderFactory, converter, objectMapper))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantAuthenticationManagerResolver(
                properties, null, jwtDecoderFactory, converter, objectMapper))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantAuthenticationManagerResolver(
                properties, tenantLookupService, null, converter, objectMapper))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantAuthenticationManagerResolver(
                properties, tenantLookupService, jwtDecoderFactory, null, objectMapper))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantAuthenticationManagerResolver(
                properties, tenantLookupService, jwtDecoderFactory, converter, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMissingMalformedMultipleOversizedOrInvalidJwtHeadersBeforeTouchingNetwork() {
        MockHttpServletRequest missingHeader = new MockHttpServletRequest("GET", "/accounts/v2/users/me");
        assertThatThrownBy(() -> resolver.resolve(missingHeader))
                .isInstanceOf(InvalidBearerTokenException.class);

        MockHttpServletRequest basicScheme = new MockHttpServletRequest("GET", "/accounts/v2/users/me");
        basicScheme.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        assertThatThrownBy(() -> resolver.resolve(basicScheme))
                .isInstanceOf(InvalidBearerTokenException.class);

        MockHttpServletRequest emptyBearer = new MockHttpServletRequest("GET", "/accounts/v2/users/me");
        emptyBearer.addHeader("Authorization", "Bearer   ");
        assertThatThrownBy(() -> resolver.resolve(emptyBearer))
                .isInstanceOf(InvalidBearerTokenException.class);

        MockHttpServletRequest multiAuth = new MockHttpServletRequest("GET", "/accounts/v2/users/me");
        multiAuth.addHeader("Authorization", "Bearer " + fakeJwt("http://localhost:8080/realms/acme"));
        multiAuth.addHeader("Authorization", "Bearer " + fakeJwt("http://localhost:8080/realms/acme"));
        assertThatThrownBy(() -> resolver.resolve(multiAuth))
                .isInstanceOf(InvalidBearerTokenException.class);

        MockHttpServletRequest oversized = new MockHttpServletRequest("GET", "/accounts/v2/users/me");
        oversized.addHeader("Authorization", "Bearer " + "a".repeat(16 * 1024 + 50));
        assertThatThrownBy(() -> resolver.resolve(oversized))
                .isInstanceOf(InvalidBearerTokenException.class);

        for (String badToken : new String[]{
                "not-a-jwt",
                "a.b",
                "a.b.c.d",
                ".payload.sig",
                "hdr..sig",
                "hdr.payload.",
                "hdr.!!!bad-base64!!!.sig",
                "hdr." + base64Url("not-json") + ".sig",
                "hdr." + base64Url("[1,2,3]") + ".sig",
                "hdr." + base64Url("{\"sub\":\"user1\"}") + ".sig",
                "hdr." + base64Url("{\"iss\":123}") + ".sig",
                "hdr." + base64Url("{\"iss\":\"   \"}") + ".sig"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/accounts/v2/users/me");
            req.addHeader("Authorization", "Bearer " + badToken);
            assertThatThrownBy(() -> resolver.resolve(req))
                    .as("Expected rejection for token %s", badToken)
                    .isInstanceOf(InvalidBearerTokenException.class);
        }

        verifyNoInteractions(tenantLookupService, jwtDecoderFactory);
    }

    @Test
    void shouldRejectSsrfIssuersBeforeTenantLookupOrDecoderCreation() {
        String[] maliciousIssuers = new String[]{
                "https://evil.example/realms/acme",
                "http://localhost:8080.evil.example/realms/acme",
                "http://localhost:8080/realms/acme" + "@" + "evil.example",
                "http://localhost:8080/realms/acme/../evil",
                "http://localhost:8080/realms/acme?redirect=http://evil.example",
                "http://localhost:8080/realms/acme#frag",
                "http://localhost:8080/realms/acme/"
        };

        for (String maliciousIss : maliciousIssuers) {
            MockHttpServletRequest req = bearerRequest("/accounts/v2/users/me", fakeJwt(maliciousIss));
            assertThatThrownBy(() -> resolver.resolve(req))
                    .as("Expected SSRF rejection for issuer %s", maliciousIss)
                    .isInstanceOf(InvalidBearerTokenException.class);
        }

        verifyNoInteractions(tenantLookupService, jwtDecoderFactory);
    }

    @Test
    void shouldEnforcePlatformRealmIsolationOnTenantManagementRoutes() {
        JwtDecoder platformDecoder = mock(JwtDecoder.class);
        String platformIss = "http://localhost:8080/realms/platform";
        Jwt decoded = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuer(platformIss)
                .subject("platform-admin-1")
                .audience(List.of("awgment-account"))
                .claim("resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-platform-admin"))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoderFactory.create(platformIss, "awgment-account")).thenReturn(platformDecoder);
        when(platformDecoder.decode(anyString())).thenReturn(decoded);

        // Exact /accounts/v2/tenants, trailing /, sub-paths, URL-encoded %74, matrix params, and //
        for (String path : new String[]{
                "/accounts/v2/tenants",
                "/accounts/v2/tenants/",
                "/accounts/v2/tenants/acme/status",
                "/accounts/v2/%74enants",
                "/accounts/v2/%74enants/acme/status",
                "/accounts/v2/tenants;v=1",
                "/accounts/v2/tenants;v=1/acme",
                "/accounts/v2//tenants/acme"
        }) {
            MockHttpServletRequest req = bearerRequest(path, fakeJwt(platformIss));
            AuthenticationManager manager = resolver.resolve(req);
            Authentication auth = manager.authenticate(new BearerTokenAuthenticationToken(fakeJwt(platformIss)));
            assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("awgment-platform-admin");

            // Tenant realm token attempting ANY normalized platform path MUST fail 403 TENANT_NOT_ACTIVE
            MockHttpServletRequest tenantBypassAttempt = bearerRequest(
                    path, fakeJwt("http://localhost:8080/realms/acme"));
            assertThatThrownBy(() -> resolver.resolve(tenantBypassAttempt))
                    .isInstanceOf(TenantAccessAuthenticationException.class)
                    .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE));
        }

        // Also cover contextPath + servletPath + pathInfo resolution
        MockHttpServletRequest contextPathReq = new MockHttpServletRequest("GET", "/api/accounts/v2/tenants");
        contextPathReq.setContextPath("/api");
        contextPathReq.addHeader("Authorization", "Bearer " + fakeJwt(platformIss));
        assertThat(resolver.resolve(contextPathReq)).isNotNull();

        MockHttpServletRequest servletPathReq = new MockHttpServletRequest("GET", "/accounts/v2/tenants/acme");
        servletPathReq.setServletPath("/accounts/v2/tenants");
        servletPathReq.setPathInfo("/acme");
        servletPathReq.addHeader("Authorization", "bearer " + fakeJwt(platformIss));
        assertThat(resolver.resolve(servletPathReq)).isNotNull();

        MockHttpServletRequest servletOnlyReq = new MockHttpServletRequest("GET", "/accounts/v2/tenants");
        servletOnlyReq.setServletPath("/accounts/v2/tenants");
        servletOnlyReq.addHeader("Authorization", "Bearer " + fakeJwt(platformIss));
        assertThat(resolver.resolve(servletOnlyReq)).isNotNull();

        MockHttpServletRequest nonMatchingContextPathReq = new MockHttpServletRequest("GET", "/accounts/v2/tenants");
        nonMatchingContextPathReq.setContextPath("/other");
        nonMatchingContextPathReq.addHeader("Authorization", "Bearer " + fakeJwt(platformIss));
        assertThat(resolver.resolve(nonMatchingContextPathReq)).isNotNull();

        HttpServletRequest nullUriReq = mock(HttpServletRequest.class);
        when(nullUriReq.getHeaders("Authorization"))
                .thenReturn(Collections.enumeration(List.of("Bearer " + fakeJwt(platformIss))));
        assertThatThrownBy(() -> resolver.resolve(nullUriReq))
                .isInstanceOf(TenantAccessAuthenticationException.class);

        HttpServletRequest nullHeadersReq = mock(HttpServletRequest.class);
        when(nullHeadersReq.getHeaders("Authorization")).thenReturn(null);
        assertThatThrownBy(() -> resolver.resolve(nullHeadersReq))
                .isInstanceOf(InvalidBearerTokenException.class);

        // Tenant realm calling /accounts/v2/tenants MUST fail 403 TENANT_NOT_ACTIVE without decoder call
        MockHttpServletRequest tenantOnPlatformRoute = bearerRequest(
                "/accounts/v2/tenants", fakeJwt("http://localhost:8080/realms/acme"));
        assertThatThrownBy(() -> resolver.resolve(tenantOnPlatformRoute))
                .isInstanceOf(TenantAccessAuthenticationException.class)
                .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE));

        // Platform realm calling non-tenant-management route (including prefix trap /tenants-other) MUST fail 403
        for (String nonPlatformPath : new String[]{"/accounts/v2/users/me", "/accounts/v2/tenants-other"}) {
            MockHttpServletRequest platformOnTenantRoute = bearerRequest(nonPlatformPath, fakeJwt(platformIss));
            assertThatThrownBy(() -> resolver.resolve(platformOnTenantRoute))
                    .isInstanceOf(TenantAccessAuthenticationException.class)
                    .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE));
        }

        verifyNoInteractions(tenantLookupService);
        verify(jwtDecoderFactory, times(1)).create(platformIss, "awgment-account");
    }

    @Test
    void shouldEnforceTenantIdValidationAndTenantStatusGateBeforeDecoderLookup() {
        // Reserved or syntactically invalid tenant realm -> 403 TENANT_NOT_ACTIVE before lookup or decoder
        for (String invalidRealm : new String[]{"master", "public", "pg_catalog", "a", "BAD_UPPER"}) {
            MockHttpServletRequest req = bearerRequest(
                    "/accounts/v2/users/me", fakeJwt("http://localhost:8080/realms/" + invalidRealm));
            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(TenantAccessAuthenticationException.class)
                    .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE));
        }
        verifyNoInteractions(tenantLookupService, jwtDecoderFactory);

        // Missing tenant
        String acmeIss = "http://localhost:8080/realms/acme";
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(acmeIss))))
                .isInstanceOf(TenantAccessAuthenticationException.class)
                .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE));

        // PROVISIONING and PROVISIONING_FAILED
        for (TenantStatus nonActive : new TenantStatus[]{TenantStatus.PROVISIONING, TenantStatus.PROVISIONING_FAILED}) {
            when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(nonActive));
            assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(acmeIss))))
                    .isInstanceOf(TenantAccessAuthenticationException.class)
                    .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE));
        }

        // SUSPENDED -> 403 TENANT_SUSPENDED
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.SUSPENDED));
        assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(acmeIss))))
                .isInstanceOf(TenantAccessAuthenticationException.class)
                .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.TENANT_SUSPENDED));

        verifyNoInteractions(jwtDecoderFactory);
    }

    @Test
    void shouldCacheActiveTenantDecoderEvictCleanlyAndBlockImmediatelyUponSuspension() {
        String acmeIss = "http://localhost:8080/realms/acme";
        String token = fakeJwt(acmeIss);
        JwtDecoder acmeDecoder = mock(JwtDecoder.class);
        Jwt decoded = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer(acmeIss)
                .subject("sub-1")
                .audience(List.of("awgment-account"))
                .claim("resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-user"))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(jwtDecoderFactory.create(acmeIss, "awgment-account")).thenReturn(acmeDecoder);
        when(acmeDecoder.decode(token)).thenReturn(decoded);

        AuthenticationManager first = resolver.resolve(bearerRequest("/accounts/v2/users/me", token));
        AuthenticationManager second = resolver.resolve(bearerRequest("/accounts/v2/users/me", token));
        assertThat(first).isSameAs(second);
        verify(jwtDecoderFactory, times(1)).create(acmeIss, "awgment-account");

        Authentication auth = first.authenticate(new BearerTokenAuthenticationToken(token));
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("awgment-user");

        // Even before explicit cache eviction, status transition to SUSPENDED blocks immediately
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.SUSPENDED));
        assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", token)))
                .isInstanceOf(TenantAccessAuthenticationException.class)
                .satisfies(ex -> assertThat(((TenantAccessAuthenticationException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.TENANT_SUSPENDED));

        // Re-activate and test explicit eviction methods
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.ACTIVE));
        resolver.evictTenant("acme");
        resolver.resolve(bearerRequest("/accounts/v2/users/me", token));
        verify(jwtDecoderFactory, times(2)).create(acmeIss, "awgment-account");

        resolver.evictIssuerCache(acmeIss);
        resolver.resolve(bearerRequest("/accounts/v2/users/me", token));
        verify(jwtDecoderFactory, times(3)).create(acmeIss, "awgment-account");

        resolver.evictAll();
        resolver.resolve(bearerRequest("/accounts/v2/users/me", token));
        verify(jwtDecoderFactory, times(4)).create(acmeIss, "awgment-account");

        // Defensive null/blank eviction calls should be safe no-ops
        resolver.evictTenant(null);
        resolver.evictTenant("  ");
        resolver.evictIssuerCache(null);
        resolver.evictIssuerCache("  ");
    }

    @Test
    void shouldWrapDecoderFactoryErrorsWithoutCachingBrokenManagers() {
        String acmeIss = "http://localhost:8080/realms/acme";
        String token = fakeJwt(acmeIss);
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.ACTIVE));

        doThrow(new IllegalStateException("Keycloak unreachable"))
                .when(jwtDecoderFactory).create(acmeIss, "awgment-account");
        assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", token)))
                .isInstanceOf(AuthenticationServiceException.class);

        doThrow(new InvalidBearerTokenException("explicit auth error"))
                .when(jwtDecoderFactory).create(acmeIss, "awgment-account");
        assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", token)))
                .isInstanceOf(InvalidBearerTokenException.class);

        doReturn(null).when(jwtDecoderFactory).create(acmeIss, "awgment-account");
        assertThatThrownBy(() -> resolver.resolve(bearerRequest("/accounts/v2/users/me", token)))
                .isInstanceOf(AuthenticationServiceException.class);

        // Next call retries because failures were never cached
        JwtDecoder recovered = mock(JwtDecoder.class);
        doReturn(recovered).when(jwtDecoderFactory).create(acmeIss, "awgment-account");
        assertThat(resolver.resolve(bearerRequest("/accounts/v2/users/me", token))).isNotNull();
        verify(jwtDecoderFactory, times(4)).create(acmeIss, "awgment-account");
    }

    @Test
    void shouldEnforceBoundedLruEvictionTtlExpiryAndNonBlockingPerIssuerBuild() throws Exception {
        AwgmentSecurityProperties cacheProps = new AwgmentSecurityProperties();
        cacheProps.setDecoderCacheMaxSize(2);
        cacheProps.setDecoderCacheTtlSeconds(60);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-23T12:00:00Z"));
        TenantAuthenticationManagerResolver boundedResolver = new TenantAuthenticationManagerResolver(
                cacheProps,
                tenantLookupService,
                jwtDecoderFactory,
                new JwtRoleConverter(cacheProps),
                objectMapper,
                clock
        );

        when(tenantLookupService.findStatus(anyString())).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(jwtDecoderFactory.create(anyString(), anyString())).thenAnswer(inv -> mock(JwtDecoder.class));

        String issA = "http://localhost:8080/realms/alpha";
        String issB = "http://localhost:8080/realms/beta";
        String issC = "http://localhost:8080/realms/gamma";

        // Populate A, B, touch A (making B least-recently-used), then add C -> B is evicted, A stays
        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issA)));
        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issB)));
        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issA)));
        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issC)));

        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issA))); // cache hit
        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issB))); // cache miss (was evicted)

        verify(jwtDecoderFactory, times(1)).create(issA, "awgment-account");
        verify(jwtDecoderFactory, times(2)).create(issB, "awgment-account");
        verify(jwtDecoderFactory, times(1)).create(issC, "awgment-account");

        // Advance past TTL -> A expires and rebuilds
        clock.advanceSeconds(60);
        boundedResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(issA)));
        verify(jwtDecoderFactory, times(2)).create(issA, "awgment-account");

        // Slow build on 'slow_t' must NOT block 'fast_t'; concurrent builds on same issuer coalesce; eviction mid-flight prevents stale cache write
        String slowIss = "http://localhost:8080/realms/slow_t";
        String fastIss = "http://localhost:8080/realms/fast_t";
        CountDownLatch slowEntered = new CountDownLatch(1);
        CountDownLatch slowRelease = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger slowFactoryCalls = new java.util.concurrent.atomic.AtomicInteger();
        JwtDecoderFactory asyncFactory = (iss, aud) -> {
            if (slowIss.equals(iss)) {
                slowFactoryCalls.incrementAndGet();
                slowEntered.countDown();
                try {
                    slowRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return mock(JwtDecoder.class);
        };
        TenantAuthenticationManagerResolver concurrentResolver = new TenantAuthenticationManagerResolver(
                cacheProps, tenantLookupService, asyncFactory, new JwtRoleConverter(cacheProps), objectMapper, clock);

        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            Future<AuthenticationManager> slowFuture1 = pool.submit(() ->
                    concurrentResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(slowIss))));
            assertThat(slowEntered.await(3, TimeUnit.SECONDS)).isTrue();

            // Fast tenant completes immediately while slow tenant is mid-discovery
            AuthenticationManager fastMgr = pool.submit(() ->
                    concurrentResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(fastIss))))
                    .get(2, TimeUnit.SECONDS);
            assertThat(fastMgr).isNotNull();

            // Wait until second slow caller reaches inFlightBuilds before releasing latch, or verify coalescing via single factory call + mid-flight eviction
            Future<AuthenticationManager> slowFuture2 = pool.submit(() ->
                    concurrentResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(slowIss))));
            Thread.sleep(50);
            concurrentResolver.evictTenant("slow_t");
            slowRelease.countDown();

            AuthenticationManager m1 = slowFuture1.get(3, TimeUnit.SECONDS);
            AuthenticationManager m2 = slowFuture2.get(3, TimeUnit.SECONDS);
            assertThat(m1).isSameAs(m2);
            assertThat(slowFactoryCalls.get()).isEqualTo(1);
            // Because evictTenant ran mid-flight, stale m1 was NOT persisted in cache
            AuthenticationManager m3 = concurrentResolver.resolve(bearerRequest("/accounts/v2/users/me", fakeJwt(slowIss)));
            assertThat(m3).isNotSameAs(m1);
            assertThat(slowFactoryCalls.get()).isEqualTo(2);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advanceSeconds(long seconds) {
            current.updateAndGet(now -> now.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    private static MockHttpServletRequest bearerRequest(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String fakeJwt(String iss) {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iss\":\"" + iss + "\",\"sub\":\"sub-1\"}");
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
