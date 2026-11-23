package com.techsophy.tsf.account.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that the checked-in Keycloak 26 Realm Blueprints satisfy every control in USE_CASES.md Section 3.
 */
class KeycloakRealmBlueprintsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void platformRealmShouldIsolatePlatformAdminRoleAndEnforceShortTtl() throws IOException {
        JsonNode realm = readRealm("keycloak/platform-realm.json");

        assertThat(realm.path("realm").asText()).isEqualTo("platform");
        assertThat(realm.path("registrationAllowed").asBoolean()).isFalse();
        assertThat(realm.path("accessTokenLifespan").asInt()).isLessThanOrEqualTo(300);

        JsonNode clientRoles = realm.path("roles").path("client").path("awgment-account");
        assertThat(clientRoles).hasSize(1);
        assertThat(clientRoles.get(0).path("name").asText()).isEqualTo("awgment-platform-admin");
    }

    @Test
    void tenantAcmeRealmShouldEnforceAllSection3SecurityControls() throws IOException {
        JsonNode realm = readRealm("keycloak/tenant-acme-realm.json");

        assertThat(realm.path("realm").asText()).isEqualTo("acme");
        assertThat(realm.path("accessTokenLifespan").asInt()).isLessThanOrEqualTo(300);
        assertThat(realm.path("verifyEmail").asBoolean()).isTrue();
        assertThat(realm.path("duplicateEmailsAllowed").asBoolean()).isFalse();

        // Control 1: Zero default Awgment roles
        JsonNode defaultRealmRole = realm.path("roles").path("realm").get(0);
        assertThat(defaultRealmRole.path("composite").asBoolean()).isFalse();

        // Control 3: First Broker Login flow disables email auto-linking
        JsonNode idp = realm.path("identityProviders").get(0);
        assertThat(idp.path("firstBrokerLoginFlowAlias").asText()).isEqualTo("first-broker-login-no-email-link");
        JsonNode flow = realm.path("authenticationFlows").get(0);
        assertThat(flow.path("alias").asText()).isEqualTo("first-broker-login-no-email-link");
        for (JsonNode execution : flow.path("authenticationExecutions")) {
            assertThat(execution.path("authenticator").asText()).doesNotContain("auto-link");
        }

        // Control 5: Declarative User Profile locks awgment_status, userId, awgment_sync_version to admin-only edit
        String profileConfigJson = realm.path("components")
                .path("org.keycloak.userprofile.UserProfileProvider")
                .get(0).path("config").path("kc.user.profile.config").get(0).asText();
        JsonNode userProfile = mapper.readTree(profileConfigJson);
        // In Keycloak 26 UPConfig.UnmanagedAttributePolicy, null/absent disables unmanaged attributes
        assertThat(userProfile.hasNonNull("unmanagedAttributePolicy")).isFalse();

        Set<String> adminOnlyLocked = new HashSet<>();
        for (JsonNode attr : userProfile.path("attributes")) {
            String name = attr.path("name").asText();
            if (Set.of("awgment_status", "userId", "awgment_sync_version").contains(name)) {
                JsonNode editRoles = attr.path("permissions").path("edit");
                assertThat(editRoles).hasSize(1);
                assertThat(editRoles.get(0).asText()).isEqualTo("admin");
                adminOnlyLocked.add(name);
            }
        }
        assertThat(adminOnlyLocked).containsExactlyInAnyOrder("awgment_status", "userId", "awgment_sync_version");

        // Control 6, 7, 8: Required protocol mappers on login client
        JsonNode webClient = findClient(realm, "awgment-web");
        Set<String> mapperNames = new HashSet<>();
        for (JsonNode pm : webClient.path("protocolMappers")) {
            mapperNames.add(pm.path("name").asText());
        }
        assertThat(mapperNames).contains(
                "awgment-account-audience",
                "identity-provider-session-note",
                "idp-tenant-id-session-note",
                "awgment-status-attribute",
                "awgment-user-id-attribute",
                "groups-membership"
        );

        // Control 4 (Tier 0): Tenant realm must NEVER define awgment-platform-admin
        for (JsonNode role : realm.path("roles").path("client").path("awgment-account")) {
            assertThat(role.path("name").asText()).isNotEqualTo("awgment-platform-admin");
        }
    }

    @Test
    void mockEntraRealmShouldEmitTidClaimForTrustedAndUntrustedTenants() throws IOException {
        JsonNode realm = readRealm("keycloak/mock-entra-realm.json");
        assertThat(realm.path("realm").asText()).isEqualTo("mock-entra");

        JsonNode brokerClient = findClient(realm, "awgment-acme-broker");
        assertThat(brokerClient.path("protocolMappers").get(0).path("config").path("claim.name").asText())
                .isEqualTo("tid");
        assertThat(realm.path("users")).hasSize(2);
    }

    private JsonNode readRealm(String classpathLocation) throws IOException {
        return mapper.readTree(new ClassPathResource(classpathLocation).getInputStream());
    }

    private JsonNode findClient(JsonNode realm, String clientId) {
        for (JsonNode client : realm.path("clients")) {
            if (clientId.equals(client.path("clientId").asText())) {
                return client;
            }
        }
        throw new AssertionError("Client not found: " + clientId);
    }
}
