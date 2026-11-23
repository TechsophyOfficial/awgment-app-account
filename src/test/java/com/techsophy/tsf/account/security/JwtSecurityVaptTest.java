package com.techsophy.tsf.account.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring Security filter-chain OWASP API Security Top 10 (2023) VAPT suite:
 * - API2: alg:none, HS256 algorithm confusion, wrong RSA key, tampered payload, expired, future nbf, missing aud, service-account spoof
 * - API5: Cross-realm Platform vs Tenant BFLA, realm_access & untrusted-client role escalation
 * - API7: SSRF iss attacks asserting zero calls to JwtDecoderFactory
 * - Tenant readiness gate (SUSPENDED -> 403 TENANT_SUSPENDED; missing/PROVISIONING -> 403 TENANT_NOT_ACTIVE)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtSecurityVaptTest.VaptProbeConfiguration.class)
class JwtSecurityVaptTest {

    private static final String PLATFORM_ISS = "http://localhost:8080/realms/platform";
    private static final String ACME_ISS = "http://localhost:8080/realms/acme";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantAuthenticationManagerResolver resolver;

    @MockBean
    private TenantLookupService tenantLookupService;

    @MockBean
    private JwtDecoderFactory jwtDecoderFactory;

    private RSAKey primaryKey;
    private JwtEncoder primaryEncoder;
    private RSAPublicKey primaryPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        resolver.evictAll();
        TenantContext.clear();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        primaryPublicKey = (RSAPublicKey) pair.getPublic();
        primaryKey = new RSAKey.Builder(primaryPublicKey)
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        primaryEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(primaryKey)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void api7_ssrfIssuerAttacksMustBeRejectedBeforeDecoderOrLookup() throws Exception {
        String[] ssrfIssuers = new String[]{
                "https://evil.example/realms/acme",
                "http://localhost:8080/realms/acme" + "@" + "evil.example",
                "http://user" + "@" + "localhost:8080/realms/acme",
                "http://localhost:8080/realms/acme/../evil",
                "http://localhost:8080/realms/acme?redirect=http://evil.example",
                "http://localhost:8080/realms/acme#fragment",
                "http://localhost:8080/realms/acme/",
                "http://localhost:8080.evil.example/realms/acme"
        };

        for (String ssrfIss : ssrfIssuers) {
            String forgedToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), ssrfIss,
                    List.of("awgment-account"), 300, null, Map.of());
            mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                            .header("Authorization", "Bearer " + forgedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(jwtDecoderFactory, tenantLookupService);
    }

    @Test
    void api2_shouldRejectAlgNoneHs256ConfusionWrongKeyTamperedExpiredFutureNbfAndMissingAud() throws Exception {
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(jwtDecoderFactory.create(ACME_ISS, "awgment-account")).thenReturn(decoderFor(ACME_ISS));

        // 1. alg:none (empty signature)
        String noneHeader = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String validClaimsJson = "{\"iss\":\"" + ACME_ISS + "\",\"sub\":\"attacker\",\"aud\":[\"awgment-account\"],\"exp\":"
                + Instant.now().plusSeconds(300).getEpochSecond() + "}";
        String algNoneToken = noneHeader + "." + base64Url(validClaimsJson) + ".";
        assertUnauthorized(algNoneToken);

        // 2. alg:none with dummy non-empty signature
        String algNoneNonEmptySig = noneHeader + "." + base64Url(validClaimsJson) + ".dummysig";
        assertUnauthorized(algNoneNonEmptySig);

        // 3. HS256 algorithm confusion
        String hs256Header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String signingInput = hs256Header + "." + base64Url(validClaimsJson);
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec("public-key-as-hmac-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hs256Token = signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        assertUnauthorized(hs256Token);

        // 4. Tampered payload on valid RS256 signature
        String validToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, null, Map.of());
        String[] parts = validToken.split("\\.");
        String tamperedPayload = base64Url("{\"iss\":\"" + ACME_ISS + "\",\"sub\":\"admin-impersonated\",\"aud\":[\"awgment-account\"],\"exp\":"
                + Instant.now().plusSeconds(300).getEpochSecond() + "}");
        assertUnauthorized(parts[0] + "." + tamperedPayload + "." + parts[2]);

        // 5. Wrong RSA key
        KeyPairGenerator otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        KeyPair otherPair = otherGen.generateKeyPair();
        RSAKey otherKey = new RSAKey.Builder((RSAPublicKey) otherPair.getPublic())
                .privateKey((RSAPrivateKey) otherPair.getPrivate())
                .keyID(primaryKey.getKeyID())
                .build();
        JwtEncoder wrongKeyEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));
        assertUnauthorized(mintRs256(wrongKeyEncoder, otherKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, null, Map.of()));

        // 6. Expired token
        assertUnauthorized(mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), -180, null, Map.of()));

        // 7. Future not-before (nbf)
        assertUnauthorized(mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, Instant.now().plusSeconds(180), Map.of()));

        // 8. Missing audience & wrong audience
        assertUnauthorized(mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                null, 300, null, Map.of()));
        assertUnauthorized(mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("other-audience"), 300, null, Map.of()));
    }

    @Test
    void api5_shouldEnforceCrossRealmBflaAndTenantStatusGatesWithoutDecoderLookup() throws Exception {
        // 1. Tenant token calling /accounts/v2/tenants/** -> 403 TENANT_NOT_ACTIVE, no WWW-Authenticate, no decoder
        String acmeAdminToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, null, Map.of(
                        "resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-platform-admin")))
                ));
        mockMvc.perform(get("/accounts/v2/tenants/vapt-probe")
                        .header("Authorization", "Bearer " + acmeAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        // 2. Platform token calling tenant route -> 403 TENANT_NOT_ACTIVE, no WWW-Authenticate, no decoder
        String platformToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), PLATFORM_ISS,
                List.of("awgment-account"), 300, null, Map.of(
                        "resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-platform-admin")))
                ));
        mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        verifyNoInteractions(jwtDecoderFactory, tenantLookupService);

        // 3. Tenant suspended -> 403 TENANT_SUSPENDED, no WWW-Authenticate, no decoder
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.SUSPENDED));
        mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                        .header("Authorization", "Bearer " + acmeAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("TENANT_SUSPENDED"));

        // 4. Tenant missing / PROVISIONING -> 403 TENANT_NOT_ACTIVE, no decoder
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.PROVISIONING));
        mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                        .header("Authorization", "Bearer " + acmeAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        verifyNoInteractions(jwtDecoderFactory);
    }

    @Test
    void api2AndApi5_shouldRejectRealmAccessEscalationServiceAccountSpoofAndCleanTenantContext() throws Exception {
        when(tenantLookupService.findStatus("acme")).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(jwtDecoderFactory.create(ACME_ISS, "awgment-account")).thenReturn(decoderFor(ACME_ISS));
        when(jwtDecoderFactory.create(PLATFORM_ISS, "awgment-account")).thenReturn(decoderFor(PLATFORM_ISS));

        // 1. Valid platform-admin succeeds on /accounts/v2/tenants/vapt-probe with TenantContext == "public"
        String validPlatformToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), PLATFORM_ISS,
                List.of("awgment-account"), 300, null, Map.of(
                        "resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-platform-admin")))
                ));
        mockMvc.perform(get("/accounts/v2/tenants/vapt-probe")
                        .header("Authorization", "Bearer " + validPlatformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantContext").value("public"));

        // 2. Forged realm_access & untrusted client role escalation MUST be ignored -> 403 ACCESS_DENIED
        String escalatedToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, null, Map.of(
                        "realm_access", Map.of("roles", List.of("awgment-account-admin")),
                        "resource_access", Map.of(
                                "awgment-app-client", Map.of("roles", List.of("awgment-account-admin")),
                                "awgment-account", Map.of("roles", List.of("awgment-user"))
                        )
                ));
        mockMvc.perform(get("/accounts/v2/vapt/admin-only")
                        .header("Authorization", "Bearer " + escalatedToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // 3. Human spoofing preferred_username="service-account-awgment-service-client" MUST NOT verify as service account
        String spoofedServiceToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, null, Map.of(
                        "azp", "awgment-web",
                        "preferred_username", "service-account-awgment-service-client",
                        "resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-user")))
                ));
        mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                        .header("Authorization", "Bearer " + spoofedServiceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantContext").value("acme"))
                .andExpect(jsonPath("$.serviceAccount").value(false));
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);

        // 4. Genuine service account verifies properly and cleans TenantContext afterwards
        String realServiceToken = mintRs256(primaryEncoder, primaryKey.getKeyID(), ACME_ISS,
                List.of("awgment-account"), 300, null, Map.of(
                        "client_id", "awgment-service-client",
                        "azp", "awgment-service-client",
                        "preferred_username", "service-account-awgment-service-client",
                        "resource_access", Map.of("awgment-account", Map.of("roles", List.of("awgment-system-service")))
                ));
        mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                        .header("Authorization", "Bearer " + realServiceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantContext").value("acme"))
                .andExpect(jsonPath("$.serviceAccount").value(true));
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/accounts/v2/vapt/tenant-probe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    private JwtDecoder decoderFor(String expectedIssuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(primaryPublicKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(expectedIssuer),
                new JwtAudienceValidator("awgment-account")
        ));
        return decoder;
    }

    private static String mintRs256(
            JwtEncoder encoder,
            String keyId,
            String iss,
            List<String> aud,
            long expiresInSeconds,
            Instant notBefore,
            Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expiresInSeconds);
        Instant iat = expiresInSeconds > 0 ? now : exp.minusSeconds(60);
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(iss)
                .subject("sub-1")
                .issuedAt(iat)
                .expiresAt(exp);
        if (aud != null) {
            builder.audience(aud);
        }
        if (notBefore != null) {
            builder.notBefore(notBefore);
        }
        extraClaims.forEach(builder::claim);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class VaptProbeConfiguration {
        @Bean
        VaptProbeEndpoints vaptProbeEndpoints(ServiceAccountPrincipalDetector detector) {
            return new VaptProbeEndpoints(detector);
        }
    }

    @TestComponent
    @RestController
    @RequestMapping("/accounts/v2")
    private static class VaptProbeEndpoints {

        private final ServiceAccountPrincipalDetector serviceAccountPrincipalDetector;

        VaptProbeEndpoints(ServiceAccountPrincipalDetector serviceAccountPrincipalDetector) {
            this.serviceAccountPrincipalDetector = serviceAccountPrincipalDetector;
        }

        @GetMapping("/tenants/vapt-probe")
        @PreAuthorize("hasAuthority('awgment-platform-admin')")
        Map<String, Object> platformProbe() {
            return Map.of("tenantContext", TenantContext.getTenantId());
        }

        @GetMapping("/vapt/tenant-probe")
        @PreAuthorize("isAuthenticated()")
        Map<String, Object> tenantProbe(@AuthenticationPrincipal Jwt jwt) {
            return Map.of(
                    "tenantContext", TenantContext.getTenantId(),
                    "serviceAccount", serviceAccountPrincipalDetector.isVerifiedServiceAccount(jwt)
            );
        }

        @GetMapping("/vapt/admin-only")
        @PreAuthorize("hasAuthority('awgment-account-admin')")
        Map<String, Object> adminOnly() {
            return Map.of("ok", true);
        }
    }
}
