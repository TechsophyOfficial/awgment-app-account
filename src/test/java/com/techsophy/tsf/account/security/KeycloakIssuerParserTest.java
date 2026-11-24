package com.techsophy.tsf.account.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakIssuerParserTest {

    @Test
    void shouldValidateBaseUrlConfiguration() {
        AwgmentSecurityProperties badScheme = new AwgmentSecurityProperties();
        badScheme.setKeycloakBaseUrl("ftp://localhost:8080");
        assertThatThrownBy(() -> new KeycloakIssuerParser(badScheme))
                .isInstanceOf(IllegalArgumentException.class);

        AwgmentSecurityProperties badQuery = new AwgmentSecurityProperties();
        badQuery.setKeycloakBaseUrl("http://localhost:8080?x=1");
        assertThatThrownBy(() -> new KeycloakIssuerParser(badQuery))
                .isInstanceOf(IllegalArgumentException.class);

        AwgmentSecurityProperties badUserinfo = new AwgmentSecurityProperties();
        badUserinfo.setKeycloakBaseUrl("http://user" + "@" + "localhost:8080");
        assertThatThrownBy(() -> new KeycloakIssuerParser(badUserinfo))
                .isInstanceOf(IllegalArgumentException.class);

        AwgmentSecurityProperties badFragment = new AwgmentSecurityProperties();
        badFragment.setKeycloakBaseUrl("http://localhost:8080#frag");
        assertThatThrownBy(() -> new KeycloakIssuerParser(badFragment))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new KeycloakIssuerParser(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldExtractValidRealmsFromNormalizedHttpAndHttpsIssuers() {
        AwgmentSecurityProperties props = new AwgmentSecurityProperties();
        props.setKeycloakBaseUrl("https://sso.example.invalid///");
        KeycloakIssuerParser parser = new KeycloakIssuerParser(props);

        assertThat(parser.extractRealm("https://sso.example.invalid/realms/acme")).contains("acme");
        assertThat(parser.extractRealm("https://sso.example.invalid/realms/platform")).contains("platform");
        assertThat(parser.extractRealm("https://sso.example.invalid/realms/tenant_1-prod")).contains("tenant_1-prod");
        assertThat(parser.issuerUriForRealm("acme")).isEqualTo("https://sso.example.invalid/realms/acme");
    }

    @Test
    void shouldRejectAllSsrfTraversalQueryFragmentUserinfoAndPrefixBypassIssuers() {
        AwgmentSecurityProperties props = new AwgmentSecurityProperties();
        KeycloakIssuerParser parser = new KeycloakIssuerParser(props);

        assertThat(parser.extractRealm(null)).isEmpty();
        assertThat(parser.extractRealm("")).isEmpty();
        assertThat(parser.extractRealm("   ")).isEmpty();
        assertThat(parser.extractRealm("https://evil.example/realms/acme")).isEmpty();
        assertThat(parser.extractRealm("https://localhost:8080/realms/acme")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080.evil.example/realms/acme")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme/")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme/extra")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme/../evil")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/..")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme.evil")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme\\evil")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme?redirect=https://evil.example")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme#fragment")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme" + "@" + "evil.example")).isEmpty();
        assertThat(parser.extractRealm("http://user" + "@" + "localhost:8080/realms/acme")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme%2f..%2fevil")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme;jsessionid=1")).isEmpty();
        assertThat(parser.extractRealm("http://localhost:8080/realms/acme withspace")).isEmpty();

        assertThatThrownBy(() -> parser.issuerUriForRealm(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.issuerUriForRealm("acme/../evil"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
