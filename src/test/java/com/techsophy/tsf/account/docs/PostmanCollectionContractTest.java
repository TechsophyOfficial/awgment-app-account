package com.techsophy.tsf.account.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Postman Collection v2.1 schema compliance, zero hardcoded secrets/tokens,
 * valid JSON & RFC 7807 ApiErrorCode parity in saved examples, and strict 1:1 endpoint
 * parity with all Spring MVC {@code /accounts/v2/**} controller mappings.
 */
@SpringBootTest
@ActiveProfiles("test")
class PostmanCollectionContractTest {

    private static final Path COLLECTION_PATH =
            Path.of("docs/postman/awgment-app-account-v2.postman_collection.json");
    private static final Path ENVIRONMENT_PATH =
            Path.of("docs/postman/awgment-local.postman_environment.json");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

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
    void postmanCollectionShouldCoverAllPhase1EndpointsWithValidExamplesAndScripts() throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(COLLECTION_PATH));
        Set<String> endpoints = new HashSet<>();
        validateItemsRecursively(root.path("item"), endpoints);

        assertThat(endpoints).contains(
                "GET /actuator/health/liveness",
                "GET /actuator/health/readiness",
                "POST /accounts/v2/tenants",
                "POST /accounts/v2/tenants/{{tenantId}}/retry-provisioning",
                "GET /accounts/v2/tenants",
                "PATCH /accounts/v2/tenants/{{tenantId}}/status"
        );

        // Verify saved example parity for 401 INVALID_TOKEN detail & 200 TenantResponse fields
        JsonNode securityFolder = root.path("item").get(3);
        String unauth401Body = securityFolder.path("item").get(1).path("response").get(0).path("body").asText();
        JsonNode unauth401Json = objectMapper.readTree(unauth401Body);
        assertThat(unauth401Json.path("detail").asText())
                .isEqualTo("Authentication is required to access this resource.");

        JsonNode tenantFolder = root.path("item").get(2);
        String list200Body = tenantFolder.path("item").get(2).path("response").get(0).path("body").asText();
        JsonNode list200Json = objectMapper.readTree(list200Body);
        assertThat(list200Json.path("content").get(0).has("keycloakContractVerified")).isTrue();
        assertThat(list200Json.path("content").get(0).has("displayName")).isTrue();

        String suspend200Body = tenantFolder.path("item").get(3).path("response").get(0).path("body").asText();
        JsonNode suspend200Json = objectMapper.readTree(suspend200Body);
        assertThat(suspend200Json.has("displayName")).isTrue();
        assertThat(suspend200Json.path("keycloakContractVerified").asBoolean()).isFalse();
    }

    @Test
    void postmanCollectionShouldHave1To1ParityWithAllAccountsV2ControllerEndpoints() throws IOException {
        Set<String> controllerApiEndpoints = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/accounts/v2")) {
                    continue;
                }
                String normalizedPath = pattern.replaceAll("\\{[^}]+\\}", "{var}");
                for (RequestMethod method : methods) {
                    controllerApiEndpoints.add(method.name() + " " + normalizedPath);
                }
            }
        }

        JsonNode root = objectMapper.readTree(Files.readString(COLLECTION_PATH));
        Set<String> rawPostmanEndpoints = new HashSet<>();
        validateItemsRecursively(root.path("item"), rawPostmanEndpoints);

        Set<String> postmanApiEndpoints = new TreeSet<>();
        for (String entry : rawPostmanEndpoints) {
            int spaceIdx = entry.indexOf(' ');
            String method = entry.substring(0, spaceIdx);
            String path = entry.substring(spaceIdx + 1);
            if (path.startsWith("/accounts/v2")) {
                postmanApiEndpoints.add(method + " " + path.replaceAll("\\{\\{[^}]+\\}\\}", "{var}"));
            }
        }

        assertThat(controllerApiEndpoints).isNotEmpty();
        assertThat(postmanApiEndpoints)
                .as("Postman collection /accounts/v2 endpoints must match Spring controllers 1:1")
                .containsExactlyInAnyOrderElementsOf(controllerApiEndpoints);
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

    private void validateItemsRecursively(JsonNode items, Set<String> endpoints) throws IOException {
        for (JsonNode item : items) {
            if (item.has("item")) {
                validateItemsRecursively(item.path("item"), endpoints);
                continue;
            }
            String requestName = item.path("name").asText();
            JsonNode req = item.path("request");
            String method = req.path("method").asText();
            String rawUrl = req.path("url").path("raw").asText();

            // Request URLs must always use parameterized host variables, never hardcoded localhost/http hosts
            assertThat(rawUrl)
                    .as("Request '%s' must parameterize host via {{baseUrl}} or {{keycloakUrl}}", requestName)
                    .doesNotContain("http://")
                    .doesNotContain("https://");

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
                    .as("Request '%s' must include a pm.test(...) script", requestName)
                    .isTrue();

            // Validate every saved response example body is valid JSON and 4xx/5xx examples match ApiErrorCode
            for (JsonNode responseExample : item.path("response")) {
                int code = responseExample.path("code").asInt();
                String body = responseExample.path("body").asText();
                if (code != 204 && !body.isBlank()) {
                    JsonNode parsedBody = objectMapper.readTree(body);
                    if (code >= 400) {
                        String errorCode = parsedBody.path("errorCode").asText();
                        assertThat(errorCode)
                                .as("Error example '%s' on '%s' must declare errorCode",
                                        responseExample.path("name").asText(), requestName)
                                .isNotBlank();
                        ApiErrorCode enumValue = ApiErrorCode.valueOf(errorCode);
                        assertThat(enumValue.status().value())
                                .as("Example HTTP code must match ApiErrorCode.%s", errorCode)
                                .isEqualTo(code);

                        boolean hasProblemJsonHeader = false;
                        for (JsonNode header : responseExample.path("header")) {
                            if ("Content-Type".equalsIgnoreCase(header.path("key").asText())
                                    && header.path("value").asText().contains("application/problem+json")) {
                                hasProblemJsonHeader = true;
                            }
                        }
                        assertThat(hasProblemJsonHeader)
                                .as("Error example '%s' must use application/problem+json",
                                        responseExample.path("name").asText())
                                .isTrue();
                    }
                }
            }
        }
    }
}
