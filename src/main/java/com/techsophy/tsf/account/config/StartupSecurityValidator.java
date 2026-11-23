package com.techsophy.tsf.account.config;

import com.techsophy.tsf.account.keycloak.KeycloakAdminProperties;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fail-fast startup validator ensuring non-local/non-test deployments never boot
 * with blank or default development passwords or unsafe Keycloak URL configuration.
 */
@Component
public class StartupSecurityValidator {

    private static final String DEV_DB_PASSWORD = "awgment_local_dev_password";
    private static final Set<String> DISALLOWED_PROD_KC_SECRETS = Set.of(
            "admin_local_dev_password",
            "awgment_local_dev_password",
            "admin_test_password"
    );

    private final Environment environment;
    private final AwgmentSecurityProperties securityProperties;
    private final KeycloakAdminProperties keycloakAdminProperties;

    public StartupSecurityValidator(
            Environment environment,
            AwgmentSecurityProperties securityProperties,
            KeycloakAdminProperties keycloakAdminProperties) {
        this.environment = environment;
        this.securityProperties = securityProperties;
        this.keycloakAdminProperties = keycloakAdminProperties;
    }

    @PostConstruct
    public void validate() {
        validateCleanBaseUrl(securityProperties.normalizedKeycloakBaseUrl(), "awgment.security.keycloak-base-url");
        String internalUrl = securityProperties.normalizedKeycloakInternalBaseUrl();
        if (!internalUrl.isEmpty()) {
            validateCleanBaseUrl(internalUrl, "awgment.security.keycloak-internal-base-url");
        }
        String adminUrl = resolveEffectiveAdminUrl();
        validateCleanBaseUrl(adminUrl, "awgment.keycloak.admin.server-url");

        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return;
        }

        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank() || DEV_DB_PASSWORD.equals(dbPassword)) {
            throw new IllegalStateException(
                    "AWGMENT_DB_PASSWORD must be explicitly configured with a non-default secret outside local/test profiles."
            );
        }

        String kcClientSecret = keycloakAdminProperties.getClientSecret();
        if (kcClientSecret != null && !kcClientSecret.isBlank()) {
            if (DISALLOWED_PROD_KC_SECRETS.contains(kcClientSecret.trim())) {
                throw new IllegalStateException(
                        "Keycloak admin client secret must not use a development default outside local/test profiles."
                );
            }
            return;
        }

        String kcUsername = keycloakAdminProperties.getUsername();
        String kcPassword = keycloakAdminProperties.getPassword();
        if (kcUsername == null || kcUsername.isBlank()
                || kcPassword == null || kcPassword.isBlank()
                || DISALLOWED_PROD_KC_SECRETS.contains(kcPassword.trim())) {
            throw new IllegalStateException(
                    "Keycloak admin credentials (AWGMENT_KEYCLOAK_ADMIN_CLIENT_SECRET or non-default AWGMENT_KEYCLOAK_ADMIN_PASSWORD) must be configured outside local/test profiles."
            );
        }
    }

    private String resolveEffectiveAdminUrl() {
        String configured = keycloakAdminProperties.getServerUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String internal = securityProperties.normalizedKeycloakInternalBaseUrl();
        return !internal.isEmpty() ? internal : securityProperties.normalizedKeycloakBaseUrl();
    }

    private static void validateCleanBaseUrl(String url, String propertyName) {
        if (url == null || url.isBlank()
                || (!url.startsWith("http://") && !url.startsWith("https://"))
                || url.indexOf('@') >= 0
                || url.indexOf('?') >= 0
                || url.indexOf('#') >= 0) {
            throw new IllegalStateException(propertyName + " must be a clean http/https base URL.");
        }
    }
}
