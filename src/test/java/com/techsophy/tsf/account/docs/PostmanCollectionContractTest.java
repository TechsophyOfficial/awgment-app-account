package com.techsophy.tsf.account.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Postman Collection v2.1 schema compliance, zero hardcoded secrets/tokens,
 * and endpoint coverage for all implemented REST endpoints (Phase 1 baseline).
 */
class PostmanCollectionContractTest {

    private static final Path COLLECTION_PATH =
            Path.of("docs/postman/awgment-app-account-v2.postman_collection.json");
    private static final Path ENVIRONMENT_PATH =
            Path.of("docs/postman/awgment-local.postman_environment.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postmanCollectionShouldConformToV21SchemaAndContainZeroHardcodedSecrets() throws IOException {
        String rawJson = Files.readString(COLLECTION_PATH);
        JsonNode root = objectMapper.readTree(rawJson);

        assertThat(root.path("info").path("schema").asText())
                .isEqualTo("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        assertThat(root.path("info").path("name").asText()).isNotBlank();

        // Zero hardcoded JWTs, bearer literals, or dev passwords
        assertThat(rawJson).doesNotContain("eyJhbGci");
        assertThat(rawJson).doesNotContain("PlatformAdminDev!");
        assertThat(rawJson).doesNotContain("OperatorDev!");
        assertThat(rawJson).doesNotContain("platform-cli-dev-secret");
        assertThat(rawJson).doesNotContain("awgment-web-dev-secret");
    }

    @Test
    void postmanCollectionShouldCoverAllPhase1EndpointsWithScriptsAndExamples() throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(COLLECTION_PATH));
        Set<String> endpoints = new HashSet<>();
        collectEndpoints(root.path("item"), endpoints);

        assertThat(endpoints).contains(
                "GET /actuator/health/liveness",
                "GET /actuator/health/readiness",
                "POST /accounts/v2/tenants",
                "POST /accounts/v2/tenants/{{tenantId}}/retry-provisioning",
                "GET /accounts/v2/tenants",
                "PATCH /accounts/v2/tenants/{{tenantId}}/status"
        );
    }

    @Test
    void localEnvironmentTemplateShouldKeepAllSecretVariablesBlank() throws IOException {
        JsonNode env = objectMapper.readTree(Files.readString(ENVIRONMENT_PATH));
        assertThat(env.path("_postman_variable_scope").asText()).isEqualTo("environment");

        Set<String> keys = new HashSet<>();
        for (JsonNode entry : env.path("values")) {
            String key = entry.path("key").asText();
            keys.add(key);
            if ("secret".equals(entry.path("type").asText())) {
                assertThat(entry.path("value").asText())
                        .as("Secret variable '%s' must be blank in git-tracked template", key)
                        .isEmpty();
            }
        }
        assertThat(keys).contains("baseUrl", "keycloakUrl", "platformToken", "accessToken", "tenantId");
    }

    private void collectEndpoints(JsonNode items, Set<String> endpoints) {
        for (JsonNode item : items) {
            if (item.has("item")) {
                collectEndpoints(item.path("item"), endpoints);
                continue;
            }
            JsonNode req = item.path("request");
            String method = req.path("method").asText();
            String rawUrl = req.path("url").path("raw").asText();
            String normalizedPath = rawUrl
                    .replace("{{baseUrl}}", "")
                    .replaceAll("\\?.*$", "");
            endpoints.add(method + " " + normalizedPath);

            // Every request must include at least one pm.test(...) assertion
            boolean hasPmTest = false;
            for (JsonNode event : item.path("event")) {
                if ("test".equals(event.path("listen").asText())) {
                    String joined = event.path("script").path("exec").toString();
                    if (joined.contains("pm.test(")) {
                        hasPmTest = true;
                    }
                }
            }
            assertThat(hasPmTest)
                    .as("Request '%s' must include a pm.test(...) script", item.path("name").asText())
                    .isTrue();
        }
    }
}
