package com.techsophy.tsf.account.config;

import com.techsophy.tsf.account.keycloak.KeycloakAdminProperties;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupSecurityValidatorTest {

    @Test
    void shouldFailFastInProductionProfileWhenDbOrKeycloakPasswordsAreBlankOrDevDefaults() {
        AwgmentSecurityProperties secProps = new AwgmentSecurityProperties();
        KeycloakAdminProperties kcProps = new KeycloakAdminProperties();
        kcProps.setUsername("admin");
        kcProps.setPassword("ProdKcPassword!");

        // Blank DB password in prod fails
        MockEnvironment blankDbEnv = new MockEnvironment()
                .withProperty("spring.datasource.password", "   ");
        StartupSecurityValidator blankDbValidator = new StartupSecurityValidator(blankDbEnv, secProps, kcProps);
        assertThatThrownBy(blankDbValidator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWGMENT_DB_PASSWORD");

        // Dev default DB password in prod fails
        MockEnvironment devDbEnv = new MockEnvironment()
                .withProperty("spring.datasource.password", "awgment_local_dev_password");
        StartupSecurityValidator devDbValidator = new StartupSecurityValidator(devDbEnv, secProps, kcProps);
        assertThatThrownBy(devDbValidator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWGMENT_DB_PASSWORD");

        // Blank KC admin password (when clientSecret is blank) fails in prod
        MockEnvironment prodDbEnv = new MockEnvironment()
                .withProperty("spring.datasource.password", "StrongProdDbPw!");
        kcProps.setPassword(" ");
        StartupSecurityValidator blankKcValidator = new StartupSecurityValidator(prodDbEnv, secProps, kcProps);
        assertThatThrownBy(blankKcValidator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keycloak admin");

        // Dev default KC admin password fails in prod
        kcProps.setPassword("admin_local_dev_password");
        StartupSecurityValidator devKcValidator = new StartupSecurityValidator(prodDbEnv, secProps, kcProps);
        assertThatThrownBy(devKcValidator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keycloak admin");
    }

    @Test
    void shouldPassInProductionWithRealCredentialsOrClientSecretAndSkipDevPasswordCheckInLocalAndTestProfiles() {
        AwgmentSecurityProperties secProps = new AwgmentSecurityProperties();
        KeycloakAdminProperties kcProps = new KeycloakAdminProperties();

        MockEnvironment prodEnv = new MockEnvironment()
                .withProperty("spring.datasource.password", "StrongProdDbPw!");

        // Client credentials mode passes even with blank password
        kcProps.setClientSecret("ProdClientSecret!");
        kcProps.setPassword("");
        assertThatCode(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .doesNotThrowAnyException();

        // Password mode with strong non-default password passes
        kcProps.setClientSecret("");
        kcProps.setUsername("awgment-admin");
        kcProps.setPassword("StrongProdKcPw!");
        assertThatCode(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .doesNotThrowAnyException();

        // Local and test profiles permit dev/empty passwords
        for (String profile : new String[]{"local", "test"}) {
            MockEnvironment nonProdEnv = new MockEnvironment()
                    .withProperty("spring.datasource.password", "awgment_local_dev_password");
            nonProdEnv.setActiveProfiles(profile);
            kcProps.setPassword("admin_local_dev_password");
            assertThatCode(() -> new StartupSecurityValidator(nonProdEnv, secProps, kcProps).validate())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldRejectInvalidKeycloakInternalUrlOrBlankKeycloakAdminUrl() {
        AwgmentSecurityProperties secProps = new AwgmentSecurityProperties();
        secProps.setKeycloakInternalBaseUrl("http://user" + "@" + "keycloak:8080");
        KeycloakAdminProperties kcProps = new KeycloakAdminProperties();
        kcProps.setPassword("StrongProdKcPw!");
        MockEnvironment localEnv = new MockEnvironment();
        localEnv.setActiveProfiles("local");

        assertThatThrownBy(() -> new StartupSecurityValidator(localEnv, secProps, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keycloak-internal-base-url");

        secProps.setKeycloakInternalBaseUrl("http://keycloak:8080");
        assertThatCode(() -> new StartupSecurityValidator(localEnv, secProps, kcProps).validate())
                .doesNotThrowAnyException();

        // Covers null/blank/bad URL schemes, query, fragment, blank serverUrl fallbacks, null DB pw, disallowed clientSecret, blank kcUsername
        kcProps.setClientSecret("awgment_local_dev_password");
        MockEnvironment prodEnv = new MockEnvironment().withProperty("spring.datasource.password", "StrongProdDbPw!");
        assertThatThrownBy(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client secret");

        kcProps.setClientSecret(null);
        kcProps.setUsername(" ");
        assertThatThrownBy(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keycloak admin credentials");

        kcProps.setUsername("kc-admin");
        kcProps.setPassword(null);
        assertThatThrownBy(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keycloak admin credentials");

        MockEnvironment nullDbEnv = new MockEnvironment();
        assertThatThrownBy(() -> new StartupSecurityValidator(nullDbEnv, secProps, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWGMENT_DB_PASSWORD");

        // Fallback admin URL resolves from internal URL or public base URL when serverUrl is blank
        kcProps.setServerUrl("  ");
        kcProps.setPassword("StrongProdKcPw!");
        assertThatCode(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .doesNotThrowAnyException();
        secProps.setKeycloakInternalBaseUrl(null);
        assertThatCode(() -> new StartupSecurityValidator(prodEnv, secProps, kcProps).validate())
                .doesNotThrowAnyException();

        for (String badUrl : new String[]{"ftp://keycloak:8080", "https://keycloak:8080?x=1", "https://keycloak:8080#frag", "keycloak:8080"}) {
            AwgmentSecurityProperties badSec = new AwgmentSecurityProperties();
            badSec.setKeycloakBaseUrl(badUrl);
            assertThatThrownBy(() -> new StartupSecurityValidator(localEnv, badSec, kcProps).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("keycloak-base-url");
        }
        AwgmentSecurityProperties nullOrBlankSec = org.mockito.Mockito.mock(AwgmentSecurityProperties.class);
        org.mockito.Mockito.when(nullOrBlankSec.normalizedKeycloakBaseUrl()).thenReturn(null, "  ");
        org.mockito.Mockito.when(nullOrBlankSec.normalizedKeycloakInternalBaseUrl()).thenReturn("");
        assertThatThrownBy(() -> new StartupSecurityValidator(localEnv, nullOrBlankSec, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keycloak-base-url");
        assertThatThrownBy(() -> new StartupSecurityValidator(localEnv, nullOrBlankSec, kcProps).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keycloak-base-url");
    }
}
