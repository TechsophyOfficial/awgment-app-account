package com.techsophy.tsf.account.user;

import com.techsophy.tsf.account.idp.TrustedIdpEntity;
import com.techsophy.tsf.account.idp.TrustedIdpRepository;
import com.techsophy.tsf.account.keycloak.KeycloakUserSyncClient;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.outbox.KeycloakOutboxRepository;
import com.techsophy.tsf.account.security.JwtAudienceValidator;
import com.techsophy.tsf.account.security.JwtDecoderFactory;
import com.techsophy.tsf.account.security.TenantAuthenticationManagerResolver;
import com.techsophy.tsf.account.support.TestJwtFactory;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OWASP API Security Top 10 (2023) VAPT suite for Phase 2 User Profile Lifecycle (UC-USER-01..07):
 * - API1 (BOLA / Cross-tenant IDOR & horizontal /users/me isolation)
 * - API3 (BOPLA / Mass assignment on POST /users, PUT /users/{id}, PUT /users/me & EXTERNAL_IDP name lock)
 * - API4 (Resource consumption / pagination bounds, unsafe sort properties & deep JSON nesting)
 * - API5 (BFLA / Separation of duties, 4-tier escalation & recursive composite role hiding)
 * - API6 (Sensitive flows / Self-disable 403 & version concurrency 409)
 * - API10 (Injection / service-account-* prefix, SQLi, system user concealment & spoofed service-account)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserLifecycleVaptTest {

    private static final String ACME_ISS = "http://localhost:8080/realms/acme";
    private static final String BETA_ISS = "http://localhost:8080/realms/beta";
    private static final String PLATFORM_ISS = "http://localhost:8080/realms/platform";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantAuthenticationManagerResolver authResolver;

    @Autowired
    private TenantLookupService tenantLookupService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrustedIdpRepository trustedIdpRepository;

    @Autowired
    private KeycloakOutboxRepository outboxRepository;

    @MockBean
    private JwtDecoderFactory jwtDecoderFactory;

    @MockBean
    private KeycloakUserSyncClient keycloakUserSyncClient;

    private TestJwtFactory jwtFactory;
    private String operatorToken;
    private String readerToken;
    private String writerOnlyToken;
    private String approverOnlyToken;
    private String basicUserToken;
    private String externalUserToken;
    private String disabledUserToken;
    private String serviceAccountToken;
    private String spoofedServiceToken;
    private String betaTenantToken;
    private String platformAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        authResolver.evictAll();
        tenantLookupService.evictAll();
        outboxRepository.deleteAll();
        userRepository.deleteAll();
        trustedIdpRepository.deleteAll();
        tenantRepository.deleteAll();
        TenantContext.clear();

        TenantEntity acme = new TenantEntity();
        acme.setId(100L);
        acme.setTenantId("acme");
        acme.setSchemaName("tenant_acme");
        acme.setDisplayName("Acme");
        acme.setStatus(TenantStatus.ACTIVE);
        tenantRepository.saveAndFlush(acme);

        TrustedIdpEntity idp = new TrustedIdpEntity();
        idp.setId(200L);
        idp.setIdpAlias("entra-acme");
        idp.setExternalTenantId("entra-tid-1");
        idp.setTenantClaimName("idp_tenant_id");
        idp.setEnabled(true);
        trustedIdpRepository.saveAndFlush(idp);

        userRepository.saveAndFlush(buildUser(
                1L, null, "system", "system" + "@" + "acme.invalid",
                IdentitySource.KEYCLOAK_LOCAL, null, UserStatus.ACTIVE, List.of()));
        userRepository.saveAndFlush(buildUser(
                2001L, "op-sub-1", "acme-operator", "operator" + "@" + "acme.invalid",
                IdentitySource.KEYCLOAK_LOCAL, null, UserStatus.ACTIVE, List.of("awgment-account-all")));
        userRepository.saveAndFlush(buildUser(
                2002L, "approver-sub-1", "acme-approver", "approver" + "@" + "acme.invalid",
                IdentitySource.KEYCLOAK_LOCAL, null, UserStatus.ACTIVE, List.of("awgment-account-admin")));
        userRepository.saveAndFlush(buildUser(
                2003L, "user-sub-1", "alice", "alice" + "@" + "acme.invalid",
                IdentitySource.KEYCLOAK_LOCAL, null, UserStatus.ACTIVE, List.of("awgment-user")));
        userRepository.saveAndFlush(buildUser(
                2004L, "ext-sub-1", "ext.alice", "ext.alice" + "@" + "acme.invalid",
                IdentitySource.EXTERNAL_IDP, "entra-acme", UserStatus.ACTIVE, List.of("awgment-user")));
        userRepository.saveAndFlush(buildUser(
                2005L, "disabled-sub-1", "disabled.bob", "disabled" + "@" + "acme.invalid",
                IdentitySource.KEYCLOAK_LOCAL, null, UserStatus.DISABLED, List.of("awgment-user")));

        // Bind custom TestJwtFactory using same keypair via JwtDecoderFactory mocks
        jwtFactory = new TestJwtFactory();
        when(jwtDecoderFactory.create(ACME_ISS, "awgment-account"))
                .thenReturn(withAudValidator(jwtFactory.decoder(), ACME_ISS));
        when(jwtDecoderFactory.create(BETA_ISS, "awgment-account"))
                .thenReturn(withAudValidator(jwtFactory.decoder(), BETA_ISS));
        when(jwtDecoderFactory.create(PLATFORM_ISS, "awgment-account"))
                .thenReturn(withAudValidator(jwtFactory.decoder(), PLATFORM_ISS));

        when(keycloakUserSyncClient.expandCompositeClosure(eq("acme"), anyCollection())).thenAnswer(inv -> {
            Collection<String> direct = inv.getArgument(1);
            Set<String> expanded = new LinkedHashSet<>(direct);
            if (expanded.contains("hidden-tier1-bundle")) {
                expanded.add("awgment-account-admin");
            }
            return expanded;
        });
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(eq("acme"), anyString())).thenReturn(Set.of());
        when(keycloakUserSyncClient.revokeUserSynchronously(eq("acme"), anyString(), anyLong(), anyString()))
                .thenReturn(true);

        operatorToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "op-sub-1", "acme-operator", List.of("awgment-account-all")));
        readerToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "reader-sub-1", "acme-reader", List.of("awgment-account-read")));
        writerOnlyToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "writer-sub-1", "acme-writer", List.of("awgment-account-create-or-update")));
        approverOnlyToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "approver-sub-1", "acme-approver", List.of("awgment-account-admin")));
        basicUserToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "user-sub-1", "alice", List.of("awgment-user")));
        externalUserToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "ext-sub-1", "ext.alice", List.of("awgment-user")));
        disabledUserToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "disabled-sub-1", "disabled.bob", List.of("awgment-user")));
        serviceAccountToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.serviceAccount(
                "acme", "awgment-service-client", List.of("awgment-account-read")));
        spoofedServiceToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "spoof-sub", "service-account-awgment-service-client", List.of("awgment-account-read")));
        betaTenantToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "beta", "beta-sub", "beta-op", List.of("awgment-account-all")));
        platformAdminToken = jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "platform", "plat-sub", "platform-admin", List.of("awgment-platform-admin")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        authResolver.evictAll();
        tenantLookupService.evictAll();
        outboxRepository.deleteAll();
        userRepository.deleteAll();
        trustedIdpRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void api1_crossTenantAndHorizontalIdorMustBeRejected() throws Exception {
        // Inactive/unknown tenant token -> 403 TENANT_NOT_ACTIVE
        mockMvc.perform(get("/accounts/v2/users/2003")
                        .header("Authorization", "Bearer " + betaTenantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        // Basic user cannot access /users or /users/{id} -> 403 ACCESS_DENIED
        mockMvc.perform(get("/accounts/v2/users/2003")
                        .header("Authorization", "Bearer " + basicUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // Disabled user calling /users/me -> 403 ACCESS_DISABLED
        mockMvc.perform(get("/accounts/v2/users/me")
                        .header("Authorization", "Bearer " + disabledUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DISABLED"));
    }

    @Test
    void api3_boplaMassAssignmentMustBeRejectedOnCreateAdminUpdateAndSelfUpdate() throws Exception {
        // Injecting status/keycloakSub into CreateUserRequest
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"newuser\",\"emailId\":\"" + "new" + "@" + "acme.invalid"
                                + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));

        // Injecting immutable userName/emailId/roles/status into PUT /users/{id}
        for (String forbiddenField : List.of("userName", "emailId", "identitySource", "idpAlias", "status", "roles", "groups")) {
            mockMvc.perform(put("/accounts/v2/users/2003")
                            .header("Authorization", "Bearer " + operatorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"Alice\",\"version\":0,\"" + forbiddenField + "\":\"tamper\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));
        }

        // Injecting department/attributes/roles/status/emailId into PUT /users/me
        for (String forbiddenField : List.of("department", "attributes", "userName", "emailId", "status", "roles", "groups")) {
            mockMvc.perform(put("/accounts/v2/users/me")
                            .header("Authorization", "Bearer " + basicUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"locale\":\"fr\",\"version\":0,\"" + forbiddenField + "\":\"tamper\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));
        }

        // EXTERNAL_IDP user trying to modify firstName/lastName on PUT /users/me
        mockMvc.perform(put("/accounts/v2/users/me")
                        .header("Authorization", "Bearer " + externalUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Tampered\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));
    }

    @Test
    void api4_paginationBoundsUnsafeSortAndDeepNestedJsonMustBeRejected() throws Exception {
        mockMvc.perform(get("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("sort", "optLockVersion,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Deeply nested JSON DoS (>1000 levels)
        int depth = 1200;
        StringBuilder nested = new StringBuilder(depth * 7);
        for (int i = 0; i < depth; i++) {
            nested.append("{\"a\":");
        }
        nested.append("1");
        for (int i = 0; i < depth; i++) {
            nested.append("}");
        }
        mockMvc.perform(put("/accounts/v2/users/me")
                        .header("Authorization", "Bearer " + basicUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"selfServicePreferences\":" + nested + "}"))
                .andExpect(status().isBadRequest());

        // Oversized JSONB selfServicePreferences (> 16 KB) rejected with 400 VALIDATION_FAILED
        String hugePref = "\"" + "x".repeat(3000) + "\"";
        StringBuilder widePrefs = new StringBuilder("{");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                widePrefs.append(',');
            }
            widePrefs.append("\"k").append(i).append("\":").append(hugePref);
        }
        widePrefs.append('}');
        mockMvc.perform(put("/accounts/v2/users/me")
                        .header("Authorization", "Bearer " + basicUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"selfServicePreferences\":" + widePrefs + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Oversized HTTP JSON request body (> 256 KB) rejected with 413 PAYLOAD_TOO_LARGE
        String oversizedBody = "{\"version\":0,\"locale\":\"en\",\"selfServicePreferences\":{\"blob\":\""
                + "z".repeat(270 * 1024) + "\"}}";
        mockMvc.perform(put("/accounts/v2/users/me")
                        .header("Authorization", "Bearer " + basicUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void api5_systemUserImmutabilityAndPhase4bFailClosedGroupsAndNoTxKeycloakIoMustHold() throws Exception {
        // Seeded system user (id = 1L) cannot be modified, disabled, or reactivated -> 403 ACCESS_DENIED
        mockMvc.perform(put("/accounts/v2/users/1")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Pwned\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(delete("/accounts/v2/users/1")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/accounts/v2/users/1/reactivate")
                        .header("Authorization", "Bearer " + approverOnlyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // Non-empty groups on POST /users rejected fail-closed until Phase 4B
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"grp.user\",\"emailId\":\"" + "grp.user" + "@" + "acme.invalid"
                                + "\",\"groups\":[\"admins\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Invalid timezone & mobileNumber rejected with 400 VALIDATION_FAILED
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"tz.user\",\"emailId\":\"" + "tz.user" + "@" + "acme.invalid"
                                + "\",\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"mob.user\",\"emailId\":\"" + "mob.user" + "@" + "acme.invalid"
                                + "\",\"mobileNumber\":\"bad<script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Prove expandCompositeClosure during POST /users executes OUTSIDE any DB transaction
        AtomicReference<Boolean> txActiveDuringKcIo = new AtomicReference<>();
        when(keycloakUserSyncClient.expandCompositeClosure(eq("acme"), anyCollection())).thenAnswer(inv -> {
            txActiveDuringKcIo.set(TransactionSynchronizationManager.isActualTransactionActive());
            Collection<?> rawRoles = inv.getArgument(1, Collection.class);
            Set<String> copied = new LinkedHashSet<>();
            for (Object role : rawRoles) {
                if (role != null) {
                    copied.add(role.toString());
                }
            }
            return copied;
        });
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"notx.user\",\"emailId\":\"" + "notx.user" + "@" + "acme.invalid"
                                + "\",\"roles\":[\"awgment-user\"]}"))
                .andExpect(status().isCreated());
        assertThat(txActiveDuringKcIo.get()).isFalse();
    }

    @Test
    void api5_bflaSeparationOfDutiesAndFourTierRoleEscalationMustBeEnforced() throws Exception {
        // Missing token -> 401
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        // Platform realm token on tenant route -> 403 TENANT_NOT_ACTIVE
        mockMvc.perform(get("/accounts/v2/users")
                        .header("Authorization", "Bearer " + platformAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_ACTIVE"));

        // Separation of duties: approver-only cannot create/delete users; operator cannot reactivate; reader cannot write
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + approverOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"valid.user\",\"emailId\":\"" + "valid.user" + "@" + "acme.invalid" + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(delete("/accounts/v2/users/2003")
                        .header("Authorization", "Bearer " + writerOnlyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/accounts/v2/users/2005/reactivate")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(put("/accounts/v2/users/2003")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // Tier 2 operator trying to assign hidden composite containing Tier 1 -> 403 ROLE_ESCALATION_FORBIDDEN
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"sneaky\",\"emailId\":\"" + "sneaky" + "@" + "acme.invalid"
                                + "\",\"roles\":[\"hidden-tier1-bundle\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_ESCALATION_FORBIDDEN"));

        // Tier 2 operator trying to assign unheld Tier 2 role -> 403 ROLE_ESCALATION_FORBIDDEN
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"aclboy\",\"emailId\":\"" + "aclboy" + "@" + "acme.invalid"
                                + "\",\"roles\":[\"awgment-acl-create-or-update\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_ESCALATION_FORBIDDEN"));

        // Tier 2 operator trying to modify or disable Tier 1 approver (2002L) -> 403 ROLE_ESCALATION_FORBIDDEN
        mockMvc.perform(put("/accounts/v2/users/2002")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Hacked\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_ESCALATION_FORBIDDEN"));

        mockMvc.perform(delete("/accounts/v2/users/2002")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_ESCALATION_FORBIDDEN"));
    }

    @Test
    void api6_selfDisableForbiddenAndVersionConflictMustBeEnforced() throws Exception {
        mockMvc.perform(delete("/accounts/v2/users/2001")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SELF_ACTION_FORBIDDEN"));

        mockMvc.perform(put("/accounts/v2/users/2003")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Ops\",\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void api10_serviceAccountPrefixSqliAndSystemUserConcealmentMustHold() throws Exception {
        // service-account-* username rejected
        mockMvc.perform(post("/accounts/v2/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"service-account-evil\",\"emailId\":\""
                                + "sa" + "@" + "acme.invalid" + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // SQLi in path variable -> 400 VALIDATION_FAILED
        mockMvc.perform(get("/accounts/v2/users/1'OR'1'='1")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Spoofed preferred_username=service-account-* without client_id/azp must NOT see 'system' user
        mockMvc.perform(get("/accounts/v2/users")
                        .header("Authorization", "Bearer " + spoofedServiceToken)
                        .param("q", "sys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // Real verified service account CAN see 'system' user
        mockMvc.perform(get("/accounts/v2/users")
                        .header("Authorization", "Bearer " + serviceAccountToken)
                        .param("q", "sys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userName").value("system"));
    }

    private static JwtDecoder withAudValidator(JwtDecoder baseDecoder, String issuer) {
        DelegatingOAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtAudienceValidator("awgment-account")
                );
        return token -> {
            org.springframework.security.oauth2.jwt.Jwt jwt = baseDecoder.decode(token);
            var res = validator.validate(jwt);
            if (res.hasErrors()) {
                throw new org.springframework.security.oauth2.jwt.JwtValidationException("invalid", res.getErrors());
            }
            return jwt;
        };
    }

    private static UserEntity buildUser(
            Long id,
            String keycloakSub,
            String userName,
            String emailId,
            IdentitySource source,
            String idpAlias,
            UserStatus status,
            List<String> roles) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setKeycloakSub(keycloakSub);
        entity.setUserName(userName);
        entity.setEmailId(emailId);
        entity.setFirstName("First");
        entity.setLastName("Last");
        entity.setLocale("en");
        entity.setSelfServicePreferences(new LinkedHashMap<>());
        entity.setAttributes(new LinkedHashMap<>());
        entity.setIdentitySource(source);
        entity.setIdpAlias(idpAlias);
        entity.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        entity.setStatus(status);
        entity.setDesiredRoles(roles);
        entity.setSyncVersion(1L);
        entity.setSyncedVersion(0L);
        entity.setSyncStatus(SyncStatus.PENDING_SYNC);
        entity.setOptLockVersion(0L);
        return entity;
    }
}
