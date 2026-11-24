package com.techsophy.tsf.account.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.outbox.KeycloakOutboxEventEntity;
import com.techsophy.tsf.account.outbox.KeycloakOutboxRepository;
import com.techsophy.tsf.account.outbox.OutboxAggregateType;
import com.techsophy.tsf.account.outbox.OutboxJobType;
import com.techsophy.tsf.account.outbox.OutboxService;
import com.techsophy.tsf.account.outbox.OutboxStatus;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.ProvisioningMode;
import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserRepository;
import com.techsophy.tsf.account.user.UserStatus;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Phase 2 Testcontainers IT against real PostgreSQL 16 + Keycloak 26.0.5
 * verifying UC-USER-01..07, UC-SYNC-01..02, and USE_CASES.md Section 10 Scenario 2
 * (sync-first revocation returning 204 vs degraded 202 + awgment_revocation_sync_failed_total).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserRevocationIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("awgment_account")
            .withUsername("awgment")
            .withPassword("awgment_test_pw");

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.5")
            .withFeaturesDisabled("organization")
            .withRealmImportFiles(
                    "keycloak/platform-realm.json",
                    "keycloak/tenant-acme-realm.json",
                    "keycloak/mock-entra-realm.json"
            );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/public");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("awgment.multitenancy.enabled", () -> "true");
        registry.add("awgment.flyway.migrate-on-startup", () -> "false");
        registry.add("awgment.outbox.worker-enabled", () -> "false");

        registry.add("awgment.security.keycloak-base-url", KEYCLOAK::getAuthServerUrl);
        registry.add("awgment.security.platform-realm", () -> "platform");
        registry.add("awgment.security.expected-audience", () -> "awgment-account");
        registry.add("awgment.security.trusted-client-id", () -> "awgment-account");

        registry.add("awgment.keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("awgment.keycloak.admin.realm", () -> "master");
        registry.add("awgment.keycloak.admin.client-id", () -> "admin-cli");
        registry.add("awgment.keycloak.admin.client-secret", () -> "");
        registry.add("awgment.keycloak.admin.username", KEYCLOAK::getAdminUsername);
        registry.add("awgment.keycloak.admin.password", KEYCLOAK::getAdminPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KeycloakOutboxRepository outboxRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private IdGeneratorImpl idGenerator;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void cleanContext() {
        TenantContext.clear();
    }

    @Test
    void shouldExecuteFullUserLifecycleLocaleSyncSyncFirstRevocationDegraded202AndOutboxRetry()
            throws Exception {
        String platformToken = fetchToken("platform", Map.of(
                "grant_type", "password",
                "client_id", "awgment-platform-cli",
                "client_secret", "platform-cli-dev-secret",
                "username", "platform-admin",
                "password", "PlatformAdminDev!"
        ));

        HttpResponse<String> provisionResp = sendApi("POST", "/accounts/v2/tenants", platformToken, """
                {
                  "tenantId": "acme",
                  "displayName": "Acme Corporation"
                }
                """);
        assertThat(provisionResp.statusCode()).isEqualTo(201);

        String operatorToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "acme-operator",
                "password", "OperatorDev!"
        ));
        String approverToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "acme-approver",
                "password", "ApproverDev!"
        ));
        String serviceToken = fetchToken("acme", Map.of(
                "grant_type", "client_credentials",
                "client_id", "awgment-service-client",
                "client_secret", "awgment-service-dev-secret"
        ));

        // Phase 1 invariant preserved: un-provisioned operator calling /users/me returns 404
        assertThat(sendApi("GET", "/accounts/v2/users/me", operatorToken, null).statusCode())
                .isEqualTo(404);

        // 1. UC-USER-01 Create User -> 201 + outbox syncs to live Keycloak
        String aliceEmail = "alice.rev" + "@" + "acme.invalid";
        HttpResponse<String> createResp = sendApi("POST", "/accounts/v2/users", operatorToken,
                "{\"userName\":\"alice.rev\",\"emailId\":\"" + aliceEmail + "\","
                        + "\"firstName\":\"Alice\",\"lastName\":\"Rev\",\"department\":\"Engineering\","
                        + "\"locale\":\"en\",\"attributes\":{\"region\":\"apac\"},\"roles\":[\"awgment-user\"]}");
        assertThat(createResp.statusCode()).isEqualTo(201);
        JsonNode aliceJson = OBJECT_MAPPER.readTree(createResp.body());
        long aliceId = aliceJson.path("id").asLong();
        assertThat(createResp.headers().firstValue("Location"))
                .contains("/accounts/v2/users/" + aliceId);

        outboxService.dispatchPendingBatch("acme", 5);
        UserEntity alicePg = loadUserInTenant("acme", aliceId);
        assertThat(alicePg.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        String aliceSub = alicePg.getKeycloakSub();
        assertThat(aliceSub).isNotBlank();

        var kcAdmin = KEYCLOAK.getKeycloakAdminClient();
        UserRepresentation kcAlice = kcAdmin.realm("acme").users().get(aliceSub).toRepresentation();
        assertThat(kcAlice.isEnabled()).isTrue();
        assertThat(kcAlice.getAttributes().get("awgment_status")).containsExactly("ACTIVE");
        assertThat(kcAlice.getAttributes().get("locale")).containsExactly("en");
        assertThat(kcAlice.getAttributes().get("userId")).containsExactly(String.valueOf(aliceId));

        // 2. UC-USER-02 & UC-USER-03 Read, Mandatory Fields & System User Visibility
        HttpResponse<String> mandatoryResp = sendApi(
                "GET", "/accounts/v2/users/" + aliceId + "?onlyMandatoryFields=true", operatorToken, null);
        assertThat(mandatoryResp.statusCode()).isEqualTo(200);
        JsonNode mandatoryJson = OBJECT_MAPPER.readTree(mandatoryResp.body());
        assertThat(mandatoryJson.path("userName").asText()).isEqualTo("alice.rev");
        assertThat(mandatoryJson.has("department")).isFalse();
        assertThat(mandatoryJson.has("attributes")).isFalse();

        HttpResponse<String> operatorSysSearch = sendApi("GET", "/accounts/v2/users?q=sys", operatorToken, null);
        assertThat(operatorSysSearch.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(operatorSysSearch.body()).path("content")).isEmpty();

        HttpResponse<String> explicitSysSearch = sendApi(
                "GET", "/accounts/v2/users?loginId=system", operatorToken, null);
        assertThat(explicitSysSearch.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(explicitSysSearch.body()).path("content").get(0).path("userName").asText())
                .isEqualTo("system");

        HttpResponse<String> svcSysSearch = sendApi("GET", "/accounts/v2/users?q=sys", serviceToken, null);
        assertThat(svcSysSearch.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(svcSysSearch.body()).path("content").get(0).path("userName").asText())
                .isEqualTo("system");

        // 3. UC-USER-01 Update User + Optimistic Locking 409 + Keycloak Locale Sync
        long currentVersion = alicePg.getOptLockVersion();
        HttpResponse<String> staleUpdate = sendApi("PUT", "/accounts/v2/users/" + aliceId, operatorToken,
                "{\"department\":\"Security\",\"version\":999}");
        assertThat(staleUpdate.statusCode()).isEqualTo(409);
        assertThat(OBJECT_MAPPER.readTree(staleUpdate.body()).path("errorCode").asText())
                .isEqualTo("CONCURRENT_MODIFICATION");

        HttpResponse<String> updateResp = sendApi("PUT", "/accounts/v2/users/" + aliceId, operatorToken,
                "{\"department\":\"Security\",\"locale\":\"fr\",\"version\":" + currentVersion + "}");
        assertThat(updateResp.statusCode()).isEqualTo(200);
        outboxService.dispatchPendingBatch("acme", 5);
        kcAlice = kcAdmin.realm("acme").users().get(aliceSub).toRepresentation();
        assertThat(kcAlice.getAttributes().get("locale")).containsExactly("fr");

        // 4. UC-USER-06 & UC-USER-07 Self-Service Profile + Keycloak Locale Sync + BOPLA Reject
        setKeycloakTestPassword(kcAdmin, "acme", aliceSub, "AliceTestPass1!");
        String aliceToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "alice.rev",
                "password", "AliceTestPass1!"
        ));

        HttpResponse<String> meGet = sendApi("GET", "/accounts/v2/users/me", aliceToken, null);
        assertThat(meGet.statusCode()).isEqualTo(200);
        JsonNode meJson = OBJECT_MAPPER.readTree(meGet.body());
        assertThat(meJson.path("locale").asText()).isEqualTo("fr");
        long meVersion = meJson.path("version").asLong();

        HttpResponse<String> meBopla = sendApi("PUT", "/accounts/v2/users/me", aliceToken,
                "{\"department\":\"HackDept\",\"version\":" + meVersion + "}");
        assertThat(meBopla.statusCode()).isEqualTo(400);
        assertThat(OBJECT_MAPPER.readTree(meBopla.body()).path("errorCode").asText())
                .isEqualTo("IMMUTABLE_FIELD_MODIFICATION");

        HttpResponse<String> meUpdate = sendApi("PUT", "/accounts/v2/users/me", aliceToken,
                "{\"locale\":\"ja\",\"selfServicePreferences\":{\"theme\":\"dark\"},\"version\":" + meVersion + "}");
        assertThat(meUpdate.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(meUpdate.body()).path("locale").asText()).isEqualTo("ja");
        outboxService.dispatchPendingBatch("acme", 5);
        kcAlice = kcAdmin.realm("acme").users().get(aliceSub).toRepresentation();
        assertThat(kcAlice.getAttributes().get("locale")).containsExactly("ja");

        // 5. Section 10 Scenario 2 (Sync-First Success): DELETE -> 204 + KC disabled + logout + /users/me 403
        assertThat(kcAdmin.realm("acme").users().get(aliceSub).getUserSessions()).isNotEmpty();

        HttpResponse<String> syncDeleteResp = sendApi("DELETE", "/accounts/v2/users/" + aliceId, operatorToken, null);
        assertThat(syncDeleteResp.statusCode()).isEqualTo(204);
        assertThat(syncDeleteResp.body()).isEmpty();

        alicePg = loadUserInTenant("acme", aliceId);
        assertThat(alicePg.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(alicePg.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        kcAlice = kcAdmin.realm("acme").users().get(aliceSub).toRepresentation();
        assertThat(kcAlice.isEnabled()).isFalse();
        assertThat(kcAlice.getAttributes().get("awgment_status")).containsExactly("DISABLED");
        assertThat(kcAdmin.realm("acme").users().get(aliceSub).getUserSessions()).isEmpty();

        HttpResponse<String> disabledMeResp = sendApi("GET", "/accounts/v2/users/me", aliceToken, null);
        assertThat(disabledMeResp.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(disabledMeResp.body()).path("errorCode").asText())
                .isEqualTo("ACCESS_DISABLED");

        // 6. UC-USER-05 Reactivate (approver only, operator gets 403)
        assertThat(sendApi("POST", "/accounts/v2/users/" + aliceId + "/reactivate", operatorToken, null).statusCode())
                .isEqualTo(403);
        HttpResponse<String> reactivateResp = sendApi(
                "POST", "/accounts/v2/users/" + aliceId + "/reactivate", approverToken, null);
        assertThat(reactivateResp.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(reactivateResp.body()).path("status").asText()).isEqualTo("ACTIVE");

        outboxService.dispatchPendingBatch("acme", 5);
        kcAlice = kcAdmin.realm("acme").users().get(aliceSub).toRepresentation();
        assertThat(kcAlice.isEnabled()).isTrue();
        assertThat(kcAlice.getAttributes().get("awgment_status")).containsExactly("ACTIVE");
        String refreshedAliceToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "alice.rev",
                "password", "AliceTestPass1!"
        ));
        assertThat(sendApi("GET", "/accounts/v2/users/me", refreshedAliceToken, null).statusCode()).isEqualTo(200);

        // 7. Self-disable 403 & Higher-tier guard 403
        String opSub = kcAdmin.realm("acme").users().searchByUsername("acme-operator", true).get(0).getId();
        String appSub = kcAdmin.realm("acme").users().searchByUsername("acme-approver", true).get(0).getId();
        long opRowId = seedExistingUserInTenant("acme", opSub, "acme-operator",
                "op" + "@" + "acme.invalid", List.of("awgment-account-all"));
        long appRowId = seedExistingUserInTenant("acme", appSub, "acme-approver",
                "app" + "@" + "acme.invalid", List.of("awgment-account-admin"));

        HttpResponse<String> selfDisableResp = sendApi(
                "DELETE", "/accounts/v2/users/" + opRowId, operatorToken, null);
        assertThat(selfDisableResp.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(selfDisableResp.body()).path("errorCode").asText())
                .isEqualTo("SELF_ACTION_FORBIDDEN");

        HttpResponse<String> higherTierResp = sendApi(
                "DELETE", "/accounts/v2/users/" + appRowId, operatorToken, null);
        assertThat(higherTierResp.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(higherTierResp.body()).path("errorCode").asText())
                .isEqualTo("ROLE_ESCALATION_FORBIDDEN");

        // 8. Section 10 Scenario 2 (Degraded KC): DELETE -> 202 + metric + immediate /users/me 403 + UC-SYNC-01/02
        String bobEmail = "bob.deg" + "@" + "acme.invalid";
        HttpResponse<String> bobCreate = sendApi("POST", "/accounts/v2/users", operatorToken,
                "{\"userName\":\"bob.deg\",\"emailId\":\"" + bobEmail + "\","
                        + "\"firstName\":\"Bob\",\"lastName\":\"Deg\",\"roles\":[\"awgment-user\"]}");
        assertThat(bobCreate.statusCode()).isEqualTo(201);
        long bobId = OBJECT_MAPPER.readTree(bobCreate.body()).path("id").asLong();
        outboxService.dispatchPendingBatch("acme", 5);
        UserEntity bobPg = loadUserInTenant("acme", bobId);
        String bobSub = bobPg.getKeycloakSub();
        assertThat(bobSub).isNotBlank();

        setKeycloakTestPassword(kcAdmin, "acme", bobSub, "BobTestPass1!");
        String bobToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "bob.deg",
                "password", "BobTestPass1!"
        ));

        double failBefore = meterRegistry.counter("awgment_revocation_sync_failed_total", "tenant", "acme").count();
        // Simulate Keycloak revocation failure against real KeycloakAdminUserSyncClient (NotFound on target sub)
        kcAdmin.realm("acme").users().get(bobSub).remove();

        HttpResponse<String> degradedDelete = sendApi("DELETE", "/accounts/v2/users/" + bobId, operatorToken, null);
        assertThat(degradedDelete.statusCode()).isEqualTo(202);
        JsonNode degradedJson = OBJECT_MAPPER.readTree(degradedDelete.body());
        assertThat(degradedJson.path("status").asText()).isEqualTo("DISABLED");
        assertThat(degradedJson.path("syncStatus").asText()).isEqualTo("PENDING_SYNC");

        double failAfter = meterRegistry.counter("awgment_revocation_sync_failed_total", "tenant", "acme").count();
        assertThat(failAfter).isGreaterThanOrEqualTo(failBefore + 1.0d);

        // Immediate local PostgreSQL revocation blocks /users/me even before outbox reconciles Keycloak!
        HttpResponse<String> bobBlockedMe = sendApi("GET", "/accounts/v2/users/me", bobToken, null);
        assertThat(bobBlockedMe.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(bobBlockedMe.body()).path("errorCode").asText())
                .isEqualTo("ACCESS_DISABLED");

        // UC-SYNC-01 inspect + UC-SYNC-02 retry FAILED outbox row
        long bobOutboxId = markOutboxFailedInTenant("acme", bobId);
        HttpResponse<String> outboxList = sendApi(
                "GET", "/accounts/v2/sync/outbox?status=FAILED&page=0&size=20", operatorToken, null);
        assertThat(outboxList.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(outboxList.body()).path("content")).isNotEmpty();

        HttpResponse<String> retryOutbox = sendApi(
                "POST", "/accounts/v2/sync/outbox/" + bobOutboxId + "/retry", operatorToken, null);
        assertThat(retryOutbox.statusCode()).isEqualTo(200);
        outboxService.dispatchPendingBatch("acme", 5);

        UserEntity bobAfterRetry = loadUserInTenant("acme", bobId);
        assertThat(bobAfterRetry.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        UserRepresentation kcBob = kcAdmin.realm("acme").users().get(bobAfterRetry.getKeycloakSub()).toRepresentation();
        assertThat(kcBob.isEnabled()).isFalse();
        assertThat(kcBob.getAttributes().get("awgment_status")).containsExactly("DISABLED");
    }

    private UserEntity loadUserInTenant(String tenantId, long userId) {
        try {
            TenantContext.setTenantId(tenantId);
            return userRepository.findById(userId).orElseThrow();
        } finally {
            TenantContext.clear();
        }
    }

    private long seedExistingUserInTenant(
            String tenantId, String sub, String userName, String email, List<String> roles) {
        try {
            TenantContext.setTenantId(tenantId);
            UserEntity existing = userRepository.findByKeycloakSub(sub).orElse(null);
            if (existing != null) {
                return existing.getId();
            }
            UserEntity entity = new UserEntity();
            entity.setId(idGenerator.nextId().longValue());
            entity.setKeycloakSub(sub);
            entity.setUserName(userName);
            entity.setEmailId(email);
            entity.setFirstName(userName);
            entity.setLastName("Test");
            entity.setLocale("en");
            entity.setSelfServicePreferences(new LinkedHashMap<>());
            entity.setAttributes(new LinkedHashMap<>());
            entity.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
            entity.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
            entity.setStatus(UserStatus.ACTIVE);
            entity.setDesiredRoles(roles);
            entity.setSyncVersion(1L);
            entity.setSyncedVersion(1L);
            entity.setSyncStatus(SyncStatus.SYNCED);
            return userRepository.saveAndFlush(entity).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private long markOutboxFailedInTenant(String tenantId, long userId) {
        try {
            TenantContext.setTenantId(tenantId);
            KeycloakOutboxEventEntity row = outboxRepository
                    .findByAggregateTypeAndAggregateIdAndJobType(
                            OutboxAggregateType.USER, userId, OutboxJobType.RECONCILE_USER)
                    .orElseThrow();
            row.setStatus(OutboxStatus.FAILED);
            row.setRetryCount(5);
            row.setLastError("Simulated transient failure");
            return outboxRepository.saveAndFlush(row).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private static void setKeycloakTestPassword(
            org.keycloak.admin.client.Keycloak kcAdmin, String realm, String userId, String password) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        kcAdmin.realm(realm).users().get(userId).resetPassword(cred);
        UserRepresentation rep = kcAdmin.realm(realm).users().get(userId).toRepresentation();
        rep.setEmailVerified(true);
        rep.setRequiredActions(List.of());
        kcAdmin.realm(realm).users().get(userId).update(rep);
    }

    private HttpResponse<String> sendApi(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String fetchToken(String realm, Map<String, String> formParams) throws Exception {
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String> entry : formParams.entrySet()) {
            if (!form.isEmpty()) {
                form.append('&');
            }
            form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("Token request failed on realm %s: %s", realm, response.body())
                .isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body()).path("access_token").asText();
    }
}
