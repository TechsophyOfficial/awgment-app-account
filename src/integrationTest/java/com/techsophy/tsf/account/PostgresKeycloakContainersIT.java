package com.techsophy.tsf.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-2 Testcontainers smoke test verifying PostgreSQL 16 + Keycloak 26.0.5
 * auto-importing platform, acme, and mock-entra realm blueprints and issuing
 * Section-3-compliant JWTs with required aud, awgment_status, and service-account claims.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresKeycloakContainersIT {

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

    @Test
    void shouldStartPostgres16AndImportAllThreeKeycloak26Realms() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             ResultSet rs = conn.createStatement().executeQuery("SELECT version()")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("PostgreSQL 16");
        }

        assertThat(KEYCLOAK.isRunning()).isTrue();
        var adminClient = KEYCLOAK.getKeycloakAdminClient();
        assertThat(adminClient.realm("platform").toRepresentation().isEnabled()).isTrue();
        assertThat(adminClient.realm("acme").toRepresentation().isEnabled()).isTrue();
        assertThat(adminClient.realm("mock-entra").toRepresentation().isEnabled()).isTrue();
    }

    @Test
    void shouldIssueSection3CompliantTokensForPlatformTenantHumanAndServiceAccount() throws Exception {
        // 1. Platform Control Realm admin token
        JsonNode platformClaims = decodeJwtPayload(fetchToken("platform", Map.of(
                "grant_type", "password",
                "client_id", "awgment-platform-cli",
                "client_secret", "platform-cli-dev-secret",
                "username", "platform-admin",
                "password", "PlatformAdminDev!"
        )));
        assertThat(platformClaims.path("iss").asText()).endsWith("/realms/platform");
        assertThat(toStringList(platformClaims.path("aud"))).contains("awgment-account");
        assertThat(toStringList(platformClaims.path("resource_access").path("awgment-account").path("roles")))
                .containsExactly("awgment-platform-admin");

        // 2. Tenant human operator token (ACTIVE + aud + scoped role + no client_id)
        JsonNode operatorClaims = decodeJwtPayload(fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "acme-operator",
                "password", "OperatorDev!"
        )));
        assertThat(operatorClaims.path("iss").asText()).endsWith("/realms/acme");
        assertThat(toStringList(operatorClaims.path("aud"))).contains("awgment-account");
        assertThat(operatorClaims.path("awgment_status").asText()).isEqualTo("ACTIVE");
        assertThat(operatorClaims.has("client_id")).isFalse();
        assertThat(toStringList(operatorClaims.path("resource_access").path("awgment-account").path("roles")))
                .contains("awgment-account-all")
                .doesNotContain("awgment-account-admin", "awgment-platform-admin");

        // 3. Tenant confidential service-account token (client_id + token_kind + awgment-system-service)
        JsonNode svcClaims = decodeJwtPayload(fetchToken("acme", Map.of(
                "grant_type", "client_credentials",
                "client_id", "awgment-service-client",
                "client_secret", "awgment-service-dev-secret"
        )));
        assertThat(svcClaims.path("iss").asText()).endsWith("/realms/acme");
        assertThat(toStringList(svcClaims.path("aud"))).contains("awgment-account");
        assertThat(svcClaims.path("client_id").asText()).isEqualTo("awgment-service-client");
        assertThat(svcClaims.path("token_kind").asText()).isEqualTo("service_account");
        assertThat(toStringList(svcClaims.path("resource_access").path("awgment-account").path("roles")))
                .contains("awgment-system-service", "awgment-acl-evaluate-context", "awgment-acl-evaluate-delegated");
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

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        return OBJECT_MAPPER.readTree(decoded);
    }

    private List<String> toStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        } else if (node.isTextual()) {
            values.add(node.asText());
        }
        return values;
    }
}
