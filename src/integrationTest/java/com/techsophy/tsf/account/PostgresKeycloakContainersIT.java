package com.techsophy.tsf.account;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-2 Testcontainers smoke test verifying PostgreSQL 16 + Keycloak 26.0.5
 * auto-importing our platform, acme, and mock-entra realm blueprints.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresKeycloakContainersIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("awgment_account")
            .withUsername("awgment")
            .withPassword("awgment_test_pw");

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.5")
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
}
