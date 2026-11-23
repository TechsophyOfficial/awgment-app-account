package com.techsophy.tsf.account.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantEntity;
import com.techsophy.tsf.account.tenant.TenantFlywayMigrator;
import com.techsophy.tsf.account.tenant.TenantRepository;
import com.techsophy.tsf.account.tenant.TenantSeeder;
import com.techsophy.tsf.account.tenant.TenantStatus;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Phase 1 Testcontainers IT against real PostgreSQL 16 + Keycloak 26.0.5
 * verifying UC-TENANT-01..04 and UC-AUTH-01 platform/tenant realm isolation.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantProvisioningIT {

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
    private TenantRepository tenantRepository;

    @Autowired
    private TenantSeeder tenantSeeder;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldExecuteFullTenantProvisioningFailureRecoveryListSuspendReactivateAndIsolationLifecycle()
            throws Exception {
        String platformToken = fetchToken("platform", Map.of(
                "grant_type", "password",
                "client_id", "awgment-platform-cli",
                "client_secret", "platform-cli-dev-secret",
                "username", "platform-admin",
                "password", "PlatformAdminDev!"
        ));
        String acmeOperatorToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "acme-operator",
                "password", "OperatorDev!"
        ));

        // 1. Unauthenticated -> 401 INVALID_TOKEN
        HttpResponse<String> unauth = sendApi("GET", "/accounts/v2/tenants", null, null);
        assertThat(unauth.statusCode()).isEqualTo(401);
        assertThat(OBJECT_MAPPER.readTree(unauth.body()).path("errorCode").asText()).isEqualTo("INVALID_TOKEN");

        // 2. Tenant token calling /accounts/v2/tenants -> 403 TENANT_NOT_ACTIVE
        HttpResponse<String> crossRealm = sendApi("GET", "/accounts/v2/tenants", acmeOperatorToken, null);
        assertThat(crossRealm.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(crossRealm.body()).path("errorCode").asText()).isEqualTo("TENANT_NOT_ACTIVE");

        // 3. Provisioning failure on nonexistent Keycloak realm commits PROVISIONING_FAILED in PostgreSQL
        HttpResponse<String> missingRealmResp = sendApi("POST", "/accounts/v2/tenants", platformToken, """
                {
                  "tenantId": "no_such_realm",
                  "displayName": "Missing Realm"
                }
                """);
        assertThat(missingRealmResp.statusCode()).isEqualTo(502);
        assertThat(OBJECT_MAPPER.readTree(missingRealmResp.body()).path("errorCode").asText())
                .isEqualTo("UPSTREAM_IDENTITY_PROVIDER_ERROR");

        TenantEntity failedMissing = tenantRepository.findByTenantId("no_such_realm").orElseThrow();
        assertThat(failedMissing.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
        assertThat(failedMissing.getLastError()).isNotBlank();

        // 4. Temporarily break acme lifespan in live Keycloak to verify PROVISIONING_FAILED -> UC-TENANT-02 recovery
        var kcAdmin = KEYCLOAK.getKeycloakAdminClient();
        RealmRepresentation acmeRep = kcAdmin.realm("acme").toRepresentation();
        Integer originalLifespan = acmeRep.getAccessTokenLifespan();
        acmeRep.setAccessTokenLifespan(600);
        kcAdmin.realm("acme").update(acmeRep);

        String bootstrapAdminEmail = "approver" + "@" + "acme.invalid";
        HttpResponse<String> failedAcmeResp = sendApi("POST", "/accounts/v2/tenants", platformToken,
                "{\"tenantId\":\"acme\",\"displayName\":\"Acme Corporation\",\"bootstrapAdminEmail\":\""
                        + bootstrapAdminEmail
                        + "\",\"bootstrapAdminFirstName\":\"Acme\",\"bootstrapAdminLastName\":\"Approver\"}");
        assertThat(failedAcmeResp.statusCode()).isEqualTo(502);
        assertThat(tenantRepository.findByTenantId("acme").orElseThrow().getStatus())
                .isEqualTo(TenantStatus.PROVISIONING_FAILED);

        // Restore compliant lifespan -> UC-TENANT-02 retry-provisioning recovers acme to ACTIVE
        acmeRep = kcAdmin.realm("acme").toRepresentation();
        acmeRep.setAccessTokenLifespan(originalLifespan);
        kcAdmin.realm("acme").update(acmeRep);

        HttpResponse<String> retryResp = sendApi(
                "POST", "/accounts/v2/tenants/acme/retry-provisioning", platformToken, null);
        assertThat(retryResp.statusCode()).isEqualTo(200);
        JsonNode retryJson = OBJECT_MAPPER.readTree(retryResp.body());
        assertThat(retryJson.path("tenantId").asText()).isEqualTo("acme");
        assertThat(retryJson.path("schemaName").asText()).isEqualTo("tenant_acme");
        assertThat(retryJson.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(retryJson.path("keycloakContractVerified").asBoolean()).isTrue();
        long activeVersion = retryJson.path("version").asLong();

        // Verify seeded SYSTEM + bootstrap admin in tenant_acme.tp_user & TenantSeeder idempotency
        tenantSeeder.seedTenant("acme", bootstrapAdminEmail, "Acme", "Approver");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tenant_acme.tp_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }

        // 5. UC-TENANT-03 List tenants + startup migrator skips PROVISIONING_FAILED & clears TenantContext
        HttpResponse<String> listResp = sendApi(
                "GET", "/accounts/v2/tenants?page=0&size=20&sort=createdAt,desc", platformToken, null);
        assertThat(listResp.statusCode()).isEqualTo(200);
        JsonNode listJson = OBJECT_MAPPER.readTree(listResp.body());
        assertThat(listJson.path("totalElements").asLong()).isEqualTo(2L);

        TenantFlywayMigrator startupMigrator = new TenantFlywayMigrator(dataSource, tenantRepository, true);
        startupMigrator.run(new DefaultApplicationArguments());
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);

        // 6. UC-TENANT-04 Suspend acme -> disables live Keycloak realm & blocks tenant tokens (403 TENANT_SUSPENDED)
        HttpResponse<String> staleSuspend = sendApi("PATCH", "/accounts/v2/tenants/acme/status", platformToken,
                "{\"status\":\"SUSPENDED\",\"reason\":\"Stale\",\"version\":0}");
        assertThat(staleSuspend.statusCode()).isEqualTo(409);
        assertThat(OBJECT_MAPPER.readTree(staleSuspend.body()).path("errorCode").asText())
                .isEqualTo("CONCURRENT_MODIFICATION");

        HttpResponse<String> suspendResp = sendApi("PATCH", "/accounts/v2/tenants/acme/status", platformToken,
                "{\"status\":\"SUSPENDED\",\"reason\":\"Maintenance\",\"version\":" + activeVersion + "}");
        assertThat(suspendResp.statusCode())
                .as("Suspend failed with body: %s", suspendResp.body())
                .isEqualTo(200);
        JsonNode suspendJson = OBJECT_MAPPER.readTree(suspendResp.body());
        assertThat(suspendJson.path("status").asText()).isEqualTo("SUSPENDED");
        long suspendedVersion = suspendJson.path("version").asLong();
        assertThat(kcAdmin.realm("acme").toRepresentation().isEnabled()).isFalse();

        HttpResponse<String> suspendedTenantCall = sendApi("GET", "/accounts/v2/users/me", acmeOperatorToken, null);
        assertThat(suspendedTenantCall.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(suspendedTenantCall.body()).path("errorCode").asText())
                .isEqualTo("TENANT_SUSPENDED");

        // 7. UC-TENANT-04 Reactivate acme -> migrates Flyway, re-enables KC realm, verifies contract, admits traffic
        HttpResponse<String> reactivateResp = sendApi("PATCH", "/accounts/v2/tenants/acme/status", platformToken,
                "{\"status\":\"ACTIVE\",\"reason\":\"Window complete\",\"version\":" + suspendedVersion + "}");
        assertThat(reactivateResp.statusCode()).isEqualTo(200);
        JsonNode reactivateJson = OBJECT_MAPPER.readTree(reactivateResp.body());
        assertThat(reactivateJson.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(reactivateJson.path("keycloakContractVerified").asBoolean()).isTrue();
        assertThat(kcAdmin.realm("acme").toRepresentation().isEnabled()).isTrue();

        // Tenant token is no longer blocked by TenantAuthenticationManagerResolver (passes auth; /users/me is Phase 2 -> 404)
        HttpResponse<String> activeTenantCall = sendApi("GET", "/accounts/v2/users/me", acmeOperatorToken, null);
        assertThat(activeTenantCall.statusCode()).isEqualTo(404);

        // Verify public.tp_security_audit_log recorded lifecycle events
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM public.tp_security_audit_log WHERE target_type = 'TENANT'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(5);
        }
    }

    private HttpResponse<String> sendApi(String method, String path, String bearerToken, String jsonBody)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String fetchToken(String realm, Map<String, String> formParams) throws Exception {
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
                .as("Token endpoint failed for realm %s: %s", realm, response.body())
                .isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body()).path("access_token").asText();
    }
}
