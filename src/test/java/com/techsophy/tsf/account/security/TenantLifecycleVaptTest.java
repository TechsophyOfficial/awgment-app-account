package com.techsophy.tsf.account.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantEntity;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantRepository;
import com.techsophy.tsf.account.tenant.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OWASP API Security Top 10 (2023) VAPT tests for UC-TENANT-01..04 endpoints:
 * - API3 (BOPLA / Mass Assignment on CreateTenantRequest & UpdateTenantStatusRequest)
 * - API4 (Pagination bounds & sort parameter abuse)
 * - API5 (BFLA: cross-realm tenant tokens & non-platform-admin tokens rejected)
 * - API10 (SQL injection & oversized/reserved tenantId rejection)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantLifecycleVaptTest {

    private static final String PLATFORM_ISS = "http://localhost:8080/realms/platform";
    private static final String ACME_ISS = "http://localhost:8080/realms/acme";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantAuthenticationManagerResolver resolver;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantLookupService tenantLookupService;

    @MockBean
    private JwtDecoderFactory jwtDecoderFactory;

    private String platformAdminToken;
    private String platformNonAdminToken;
    private String acmeTenantToken;

    @BeforeEach
    void setUp() throws Exception {
        resolver.evictAll();
        tenantLookupService.evictAll();
        tenantRepository.deleteAll();
        TenantContext.clear();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

        when(jwtDecoderFactory.create(PLATFORM_ISS, "awgment-account"))
                .thenReturn(decoderFor(publicKey, PLATFORM_ISS));
        when(jwtDecoderFactory.create(ACME_ISS, "awgment-account"))
                .thenReturn(decoderFor(publicKey, ACME_ISS));

        platformAdminToken = mintToken(encoder, rsaKey.getKeyID(), PLATFORM_ISS, List.of("awgment-platform-admin"));
        platformNonAdminToken = mintToken(encoder, rsaKey.getKeyID(), PLATFORM_ISS, List.of("awgment-user"));
        acmeTenantToken = mintToken(encoder, rsaKey.getKeyID(), ACME_ISS, List.of("awgment-platform-admin"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        tenantLookupService.evictAll();
        tenantRepository.deleteAll();
    }

    @Test
    void api3_boplaMassAssignmentMustBeRejectedOnCreateAndStatusUpdate() throws Exception {
        mockMvc.perform(post("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "beta",
                                  "displayName": "Beta Corp",
                                  "status": "ACTIVE",
                                  "schemaName": "public"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));

        mockMvc.perform(patch("/accounts/v2/tenants/beta/status")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED",
                                  "reason": "test",
                                  "version": 1,
                                  "tenantId": "other"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));
    }

    @Test
    void api4_paginationBoundsAndUnsafeSortPropertiesMustBeRejected() throws Exception {
        String excessiveSize = String.valueOf(1000 * 1000);
        mockMvc.perform(get("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .param("size", excessiveSize))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .param("sort", "optLockVersion,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void api5_nonPlatformRealmAndNonPlatformAdminMustBeRejectedAcrossAllTenantRoutes() throws Exception {
        // Tenant realm JWT holding spoofed awgment-platform-admin -> 403 TENANT_NOT_ACTIVE
        mockMvc.perform(post("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + acmeTenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"beta\",\"displayName\":\"Beta\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        mockMvc.perform(patch("/accounts/v2/tenants/acme/status")
                        .header("Authorization", "Bearer " + acmeTenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        // Platform realm JWT without awgment-platform-admin authority -> 403 ACCESS_DENIED
        mockMvc.perform(get("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformNonAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/accounts/v2/tenants/acme/retry-provisioning")
                        .header("Authorization", "Bearer " + platformNonAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void api10_sqliReservedAnd51CharTenantIdsMustReturn400InvalidTenantId() throws Exception {
        String[] maliciousTenantIds = new String[]{
                "acme' OR '1'='1",
                "acme; DROP TABLE tp_tenant",
                "public",
                "information_schema",
                "pg_catalog",
                "a" + "b".repeat(50)
        };

        for (String payload : maliciousTenantIds) {
            mockMvc.perform(post("/accounts/v2/tenants")
                            .header("Authorization", "Bearer " + platformAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + payload.replace("\"", "\\\"") + "\",\"displayName\":\"Test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TENANT_ID"));
        }

        // Verify valid platform-admin listing works cleanly with persisted entity
        TenantEntity entity = new TenantEntity();
        entity.setId(123L);
        entity.setTenantId("acme");
        entity.setSchemaName("tenant_acme");
        entity.setDisplayName("Acme");
        entity.setStatus(TenantStatus.ACTIVE);
        tenantRepository.saveAndFlush(entity);

        mockMvc.perform(get("/accounts/v2/tenants")
                        .header("Authorization", "Bearer " + platformAdminToken)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tenantId").value("acme"));
    }

    private static JwtDecoder decoderFor(RSAPublicKey publicKey, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtAudienceValidator("awgment-account")
        ));
        return decoder;
    }

    private static String mintToken(JwtEncoder encoder, String keyId, String iss, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(iss)
                .subject("vapt-actor")
                .audience(List.of("awgment-account"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("resource_access", Map.of("awgment-account", Map.of("roles", roles)))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
