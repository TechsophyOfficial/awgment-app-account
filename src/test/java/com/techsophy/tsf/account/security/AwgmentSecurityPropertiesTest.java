package com.techsophy.tsf.account.security;

import com.techsophy.tsf.account.error.ApiErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwgmentSecurityPropertiesTest {

    @Test
    void shouldProvideSafeProductionDefaults() {
        AwgmentSecurityProperties props = new AwgmentSecurityProperties();

        assertThat(props.getPlatformRealm()).isEqualTo("platform");
        assertThat(props.getExpectedAudience()).isEqualTo("awgment-account");
        assertThat(props.getTrustedClientId()).isEqualTo("awgment-account");
        assertThat(props.getKeycloakBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.normalizedKeycloakBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.isDevDocsEnabled()).isFalse();
        assertThat(props.getServiceAccountClients()).containsExactly("awgment-service-client");
    }

    @Test
    void shouldNormalizeTrimAndFallbackBlankOrNullFieldsSafely() {
        AwgmentSecurityProperties props = new AwgmentSecurityProperties();

        props.setPlatformRealm("  custom-platform  ");
        props.setExpectedAudience("  custom-aud ");
        props.setTrustedClientId(" custom-client ");
        props.setKeycloakBaseUrl(" https://sso.example.invalid/// ");
        props.setDevDocsEnabled(true);
        props.setServiceAccountClients(Arrays.asList(" svc-a ", null, "  ", "svc-a", "svc-b"));

        assertThat(props.getPlatformRealm()).isEqualTo("custom-platform");
        assertThat(props.getExpectedAudience()).isEqualTo("custom-aud");
        assertThat(props.getTrustedClientId()).isEqualTo("custom-client");
        assertThat(props.getKeycloakBaseUrl()).isEqualTo("https://sso.example.invalid///");
        assertThat(props.normalizedKeycloakBaseUrl()).isEqualTo("https://sso.example.invalid");
        assertThat(props.isDevDocsEnabled()).isTrue();
        assertThat(props.getServiceAccountClients()).containsExactly("svc-a", "svc-b");

        props.setPlatformRealm("   ");
        props.setExpectedAudience(null);
        props.setTrustedClientId("");
        props.setKeycloakBaseUrl("   ");
        props.setServiceAccountClients(null);

        assertThat(props.getPlatformRealm()).isEqualTo("platform");
        assertThat(props.getExpectedAudience()).isEqualTo("awgment-account");
        assertThat(props.getTrustedClientId()).isEqualTo("awgment-account");
        assertThat(props.getKeycloakBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.getServiceAccountClients()).isEmpty();

        props.setPlatformRealm(null);
        props.setExpectedAudience("  ");
        props.setTrustedClientId(null);
        props.setKeycloakBaseUrl(null);
        props.setServiceAccountClients(List.of());

        assertThat(props.getPlatformRealm()).isEqualTo("platform");
        assertThat(props.getExpectedAudience()).isEqualTo("awgment-account");
        assertThat(props.getTrustedClientId()).isEqualTo("awgment-account");
        assertThat(props.getKeycloakBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.getServiceAccountClients()).isEmpty();
    }

    @Test
    void tenantAccessAuthenticationExceptionShouldPreserveErrorCodeAndRejectNullCode() {
        TenantAccessAuthenticationException direct = new TenantAccessAuthenticationException(
                ApiErrorCode.TENANT_SUSPENDED, "Tenant suspended.");
        assertThat(direct.errorCode()).isEqualTo(ApiErrorCode.TENANT_SUSPENDED);
        assertThat(direct.getMessage()).isEqualTo("Tenant suspended.");

        IllegalStateException cause = new IllegalStateException("root");
        TenantAccessAuthenticationException withCause = new TenantAccessAuthenticationException(
                ApiErrorCode.TENANT_NOT_ACTIVE, "Not active", cause);
        assertThat(withCause.errorCode()).isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE);
        assertThat(withCause.getCause()).isSameAs(cause);

        TenantAccessAuthenticationException blankMessage = new TenantAccessAuthenticationException(
                ApiErrorCode.TENANT_NOT_ACTIVE, "   ");
        assertThat(blankMessage.getMessage()).isEqualTo(ApiErrorCode.TENANT_NOT_ACTIVE.title());

        TenantAccessAuthenticationException nullMessage = new TenantAccessAuthenticationException(
                ApiErrorCode.TENANT_SUSPENDED, null, cause);
        assertThat(nullMessage.getMessage()).isEqualTo(ApiErrorCode.TENANT_SUSPENDED.title());

        assertThatThrownBy(() -> new TenantAccessAuthenticationException(null, "msg"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantAccessAuthenticationException(null, "msg", cause))
                .isInstanceOf(NullPointerException.class);
    }
}
