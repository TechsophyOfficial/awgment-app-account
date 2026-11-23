package com.techsophy.tsf.account.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.ClientContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.IdpContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.IdpMapperContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.ProtocolMapperContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.UserProfileContract;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakRealmContractVerifierTest {

    private static final String EXPECTED_AUDIENCE = "awgment-account";
    private static final String PLATFORM_REALM = "platform";

    private final KeycloakRealmContractVerifier verifier =
            new KeycloakRealmContractVerifier(EXPECTED_AUDIENCE, PLATFORM_REALM);

    @Test
    void shouldPassValidSnapshotWithNullUnmanagedAttributePolicy() {
        assertThatCode(() -> verifier.verify(validSnapshot())).doesNotThrowAnyException();
    }

    @Test
    void shouldPassValidSnapshotWithAdminEditUnmanagedAttributePolicyAndAdminSyncAndBuiltinClients() {
        List<ClientContract> clients = new ArrayList<>(validSnapshot().clients());
        clients.add(new ClientContract("awgment-admin-sync", true, false, false, false, true, List.of()));
        clients.add(new ClientContract("account-console", true, false, true, false, false, List.of()));
        clients.add(new ClientContract("admin-cli", true, false, false, true, false, List.of()));
        clients.add(new ClientContract("disabled-web", false, false, true, true, false, List.of()));

        KeycloakRealmContractSnapshot snapshot = withUserProfile(
                withClients(validSnapshot(), clients),
                new UserProfileContract("ADMIN_EDIT", validAttributeEditPermissions())
        );

        assertThatCode(() -> verifier.verify(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassTenantAcmeRealmBlueprintFromClasspath() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode realm = mapper.readTree(new ClassPathResource("keycloak/tenant-acme-realm.json").getInputStream());

        String profileJson = realm.path("components")
                .path("org.keycloak.userprofile.UserProfileProvider")
                .get(0).path("config").path("kc.user.profile.config").get(0).asText();
        JsonNode profileNode = mapper.readTree(profileJson);
        String policy = profileNode.hasNonNull("unmanagedAttributePolicy")
                ? profileNode.path("unmanagedAttributePolicy").asText()
                : null;
        Map<String, Set<String>> editPermissions = new LinkedHashMap<>();
        for (JsonNode attr : profileNode.path("attributes")) {
            Set<String> editSet = new java.util.LinkedHashSet<>();
            for (JsonNode role : attr.path("permissions").path("edit")) {
                editSet.add(role.asText());
            }
            editPermissions.put(attr.path("name").asText(), editSet);
        }

        List<IdpMapperContract> idpMappers = new ArrayList<>();
        for (JsonNode m : realm.path("identityProviderMappers")) {
            Map<String, String> cfg = new LinkedHashMap<>();
            m.path("config").fields().forEachRemaining(e -> cfg.put(e.getKey(), e.getValue().asText()));
            idpMappers.add(new IdpMapperContract(
                    m.path("name").asText(),
                    m.path("identityProviderMapper").asText(),
                    cfg
            ));
        }

        List<IdpContract> idps = new ArrayList<>();
        for (JsonNode idp : realm.path("identityProviders")) {
            String alias = idp.path("alias").asText();
            idps.add(new IdpContract(
                    alias,
                    idp.path("providerId").asText(),
                    idp.path("enabled").asBoolean(),
                    idp.path("firstBrokerLoginFlowAlias").asText(),
                    idp.path("config").path("issuer").asText(),
                    idpMappers
            ));
        }

        Map<String, List<String>> flows = new LinkedHashMap<>();
        for (JsonNode flow : realm.path("authenticationFlows")) {
            List<String> execs = new ArrayList<>();
            for (JsonNode ex : flow.path("authenticationExecutions")) {
                execs.add(ex.path("authenticator").asText());
            }
            flows.put(flow.path("alias").asText(), execs);
        }

        List<ClientContract> clients = new ArrayList<>();
        for (JsonNode client : realm.path("clients")) {
            List<ProtocolMapperContract> pms = new ArrayList<>();
            for (JsonNode pm : client.path("protocolMappers")) {
                Map<String, String> cfg = new LinkedHashMap<>();
                pm.path("config").fields().forEachRemaining(e -> cfg.put(e.getKey(), e.getValue().asText()));
                pms.add(new ProtocolMapperContract(
                        pm.path("name").asText(),
                        pm.path("protocolMapper").asText(),
                        cfg
                ));
            }
            clients.add(new ClientContract(
                    client.path("clientId").asText(),
                    client.path("enabled").asBoolean(),
                    client.path("bearerOnly").asBoolean(false),
                    client.path("standardFlowEnabled").asBoolean(false),
                    client.path("directAccessGrantsEnabled").asBoolean(false),
                    client.path("serviceAccountsEnabled").asBoolean(false),
                    pms
            ));
        }

        KeycloakRealmContractSnapshot snapshot = new KeycloakRealmContractSnapshot(
                realm.path("realm").asText(),
                realm.path("enabled").asBoolean(),
                realm.path("verifyEmail").asBoolean(),
                realm.path("registrationAllowed").asBoolean(),
                realm.path("duplicateEmailsAllowed").asBoolean(),
                realm.path("internationalizationEnabled").asBoolean(),
                realm.path("defaultLocale").asText(),
                realm.path("accessTokenLifespan").asInt(),
                Set.of(),
                idps,
                flows,
                new UserProfileContract(policy, editPermissions),
                clients
        );

        assertThatCode(() -> verifier.verify(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullSnapshotAndInvalidRealmIdentityOrDisabledRealm() {
        assertThatThrownBy(() -> verifier.verify(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        KeycloakRealmContractSnapshot base = validSnapshot();
        KeycloakRealmContractSnapshot platformSnapshot = new KeycloakRealmContractSnapshot(
                "platform", base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        );
        assertThatThrownBy(() -> verifier.verify(platformSnapshot))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("platform");

        KeycloakRealmContractSnapshot disabledRealm = new KeycloakRealmContractSnapshot(
                "acme", false, base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        );
        assertThatThrownBy(() -> verifier.verify(disabledRealm))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("enabled");
    }

    @Test
    void shouldRejectExcessiveTokenLifespanDuplicateEmailsUnverifiedRegistrationAndDefaultAwgmentRoles() {
        KeycloakRealmContractSnapshot base = validSnapshot();

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                301, base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("accessTokenLifespan");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                true, base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("duplicateEmailsAllowed");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), false, true,
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("verifyEmail");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), Set.of("awgment-user"), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("awgment-");

        // Tenant realm must NEVER define awgment-platform-admin (realm or trusted client) or awgment-* defaultRole/defaultGroup
        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), "default-roles-acme", base.defaultRoleComposites(),
                Set.of(), Set.of("awgment-platform-admin"), Set.of(),
                base.identityProviders(), base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("awgment-platform-admin");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), "default-roles-acme", base.defaultRoleComposites(),
                Set.of(), Set.of(), Set.of("awgment-platform-admin"),
                base.identityProviders(), base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("awgment-platform-admin");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), "awgment-user", Set.of(),
                Set.of(), Set.of(), Set.of(),
                base.identityProviders(), base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("awgment-user");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), "default-roles-acme", Set.of(),
                Set.of("awgment-account-read"), Set.of(), Set.of(),
                base.identityProviders(), base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("awgment-account-read");
    }

    @Test
    void shouldRejectDisabledInternationalizationOrMissingDefaultLocale() {
        KeycloakRealmContractSnapshot base = validSnapshot();

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), false, "en",
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("internationalization");

        assertThatThrownBy(() -> verifier.verify(new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), true, " ",
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), base.clients()
        ))).isInstanceOf(ApiException.class).hasMessageContaining("defaultLocale");
    }

    @Test
    void shouldRejectMultiTenantIdpIssuersMissingSessionMapperOrAutoLinkBrokerFlows() {
        KeycloakRealmContractSnapshot base = validSnapshot();
        IdpContract validIdp = base.identityProviders().getFirst();

        for (String multiTenantIssuer : List.of(
                "https://login.example.invalid/common/v2.0",
                "https://login.example.invalid/organizations/v2.0",
                "https://login.example.invalid/consumers/v2.0",
                "  "
        )) {
            IdpContract badIssuerIdp = new IdpContract(
                    validIdp.alias(), "oidc", true, validIdp.firstBrokerLoginFlowAlias(),
                    multiTenantIssuer, validIdp.mappers()
            );
            assertThatThrownBy(() -> verifier.verify(withIdps(base, List.of(badIssuerIdp), base.authFlowExecutions())))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("issuer");
        }

        IdpContract missingMapperIdp = new IdpContract(
                validIdp.alias(), "oidc", true, validIdp.firstBrokerLoginFlowAlias(),
                validIdp.issuer(), List.of()
        );
        assertThatThrownBy(() -> verifier.verify(withIdps(base, List.of(missingMapperIdp), base.authFlowExecutions())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("idp_tenant_id");

        for (String forbiddenAuth : List.of(
                "idp-auto-link",
                "idp-confirm-link",
                "idp-detect-existing-broker-user",
                "idp-email-verification"
        )) {
            assertThatThrownBy(() -> verifier.verify(withIdps(
                    base,
                    List.of(validIdp),
                    Map.of("first-broker-login-no-email-link", List.of("idp-create-user-if-unique", forbiddenAuth))
            ))).isInstanceOf(ApiException.class)
                    .hasMessageContaining(forbiddenAuth);
        }

        // Disabled OIDC IdP should not block verification
        IdpContract disabledMultiTenant = new IdpContract(
                "disabled-idp", "oidc", false, "", "https://login.example.invalid/common/v2.0", List.of()
        );
        assertThatCode(() -> verifier.verify(withIdps(base, List.of(validIdp, disabledMultiTenant), base.authFlowExecutions())))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsafeUserProfilePolicyOrUnlockedRequiredAttributes() {
        KeycloakRealmContractSnapshot base = validSnapshot();

        assertThatThrownBy(() -> verifier.verify(withUserProfile(base, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("UserProfile");

        for (String badPolicy : List.of("ENABLED", "ADMIN_VIEW")) {
            assertThatThrownBy(() -> verifier.verify(withUserProfile(
                    base, new UserProfileContract(badPolicy, validAttributeEditPermissions()))))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("unmanagedAttributePolicy");
        }

        Map<String, Set<String>> missingLocale = new HashMap<>(validAttributeEditPermissions());
        missingLocale.remove("locale");
        assertThatThrownBy(() -> verifier.verify(withUserProfile(
                base, new UserProfileContract(null, missingLocale))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("locale");

        Map<String, Set<String>> userEditableStatus = new HashMap<>(validAttributeEditPermissions());
        userEditableStatus.put("awgment_status", Set.of("admin", "user"));
        assertThatThrownBy(() -> verifier.verify(withUserProfile(
                base, new UserProfileContract(null, userEditableStatus))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("awgment_status");
    }

    @Test
    void shouldRejectInteractiveLoginClientsMissingAudienceOrSessionNoteMappers() {
        KeycloakRealmContractSnapshot base = validSnapshot();
        ClientContract webClient = base.clients().getFirst();

        // Missing audience mapper
        List<ProtocolMapperContract> noAud = webClient.protocolMappers().stream()
                .filter(m -> !"oidc-audience-mapper".equals(m.protocolMapper()))
                .toList();
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                new ClientContract("awgment-web", true, false, true, true, false, noAud)
        )))).isInstanceOf(ApiException.class).hasMessageContaining("oidc-audience-mapper");

        // Missing identity_provider mapper
        List<ProtocolMapperContract> noIdpNote = webClient.protocolMappers().stream()
                .filter(m -> !"identity_provider".equals(m.config().get("user.session.note")))
                .toList();
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                new ClientContract("awgment-web", true, false, true, true, false, noIdpNote)
        )))).isInstanceOf(ApiException.class).hasMessageContaining("identity_provider");

        // Missing idp_tenant_id mapper
        List<ProtocolMapperContract> noIdpTenantNote = webClient.protocolMappers().stream()
                .filter(m -> !"idp_tenant_id".equals(m.config().get("user.session.note")))
                .toList();
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                new ClientContract("awgment-web", true, false, true, true, false, noIdpTenantNote)
        )))).isInstanceOf(ApiException.class).hasMessageContaining("idp_tenant_id");

        // Missing awgment_status attribute mapper
        List<ProtocolMapperContract> noAwgmentStatus = webClient.protocolMappers().stream()
                .filter(m -> !"awgment_status".equals(m.config().get("user.attribute")))
                .toList();
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                new ClientContract("awgment-web", true, false, true, true, false, noAwgmentStatus)
        )))).isInstanceOf(ApiException.class).hasMessageContaining("awgment_status");

        // Missing userId attribute mapper
        List<ProtocolMapperContract> noUserId = webClient.protocolMappers().stream()
                .filter(m -> !"userId".equals(m.config().get("user.attribute")))
                .toList();
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                new ClientContract("awgment-web", true, false, true, true, false, noUserId)
        )))).isInstanceOf(ApiException.class).hasMessageContaining("userId");

        // No interactive login clients at all
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                new ClientContract("awgment-account", true, true, false, false, false, List.of())
        )))).isInstanceOf(ApiException.class).hasMessageContaining("interactive");

        // Service account client (non-admin-sync) without audience mapper
        ClientContract webClientOnly = base.clients().getFirst();
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(
                webClientOnly,
                new ClientContract("custom-service", true, false, false, false, true, List.of())
        )))).isInstanceOf(ApiException.class).hasMessageContaining("Service-account client");

        // included.custom.audience accepted; access.token.claim=false rejected
        ClientContract customAudWeb = new ClientContract(
                "awgment-web",
                true,
                false,
                true,
                false,
                false,
                List.of(
                        new ProtocolMapperContract("custom-aud", "oidc-audience-mapper",
                                Map.of("included.custom.audience", EXPECTED_AUDIENCE, "access.token.claim", "true")),
                        new ProtocolMapperContract("idp", "oidc-usersessionmodel-note-mapper",
                                Map.of("user.session.note", "identity_provider", "claim.name", "identity_provider", "access.token.claim", "true")),
                        new ProtocolMapperContract("idp-tid", "oidc-usersessionmodel-note-mapper",
                                Map.of("user.session.note", "idp_tenant_id", "claim.name", "idp_tenant_id", "access.token.claim", "true")),
                        new ProtocolMapperContract("awgment-status", "oidc-usermodel-attribute-mapper",
                                Map.of("user.attribute", "awgment_status", "claim.name", "awgment_status", "access.token.claim", "true")),
                        new ProtocolMapperContract("user-id", "oidc-usermodel-attribute-mapper",
                                Map.of("user.attribute", "userId", "claim.name", "userId", "access.token.claim", "true"))
                )
        );
        assertThatCode(() -> verifier.verify(withClients(base, List.of(customAudWeb)))).doesNotThrowAnyException();

        ClientContract claimDisabledWeb = new ClientContract(
                "awgment-web",
                true,
                false,
                true,
                false,
                false,
                List.of(new ProtocolMapperContract("aud", "oidc-audience-mapper",
                        Map.of("included.client.audience", EXPECTED_AUDIENCE, "access.token.claim", "false")))
        );
        assertThatThrownBy(() -> verifier.verify(withClients(base, List.of(claimDisabledWeb))))
                .isInstanceOf(ApiException.class).hasMessageContaining("oidc-audience-mapper");
    }

    @Test
    void shouldHandleConstructorPropertiesNullCollectionsNonOidcIdpsAndAdditionalEdgeCases() {
        com.techsophy.tsf.account.security.AwgmentSecurityProperties secProps =
                new com.techsophy.tsf.account.security.AwgmentSecurityProperties();
        KeycloakRealmContractVerifier propsVerifier = new KeycloakRealmContractVerifier(secProps);
        assertThatCode(() -> propsVerifier.verify(validSnapshot())).doesNotThrowAnyException();

        // Snapshot compact constructors handle null collections cleanly
        KeycloakRealmContractSnapshot emptySnapshot = new KeycloakRealmContractSnapshot(
                null, false, false, true, false, false, null, null,
                null, null, null, new UserProfileContract(null, null), null
        );
        assertThat(emptySnapshot.defaultRoleComposites()).isEmpty();
        assertThat(emptySnapshot.identityProviders()).isEmpty();
        assertThat(emptySnapshot.authFlowExecutions()).isEmpty();
        assertThat(emptySnapshot.clients()).isEmpty();
        assertThat(emptySnapshot.userProfile().attributeEditPermissions()).isEmpty();
        ClientContract nullMappersClient = new ClientContract("c", true, false, false, false, false, null);
        assertThat(nullMappersClient.protocolMappers()).isEmpty();
        IdpMapperContract nullMapperCfg = new IdpMapperContract("m", "type", null);
        assertThat(nullMapperCfg.config()).isEmpty();
        Map<String, Set<String>> nullPermMap = new HashMap<>();
        nullPermMap.put("k", null);
        UserProfileContract nullPermEntry = new UserProfileContract(null, nullPermMap);
        assertThat(nullPermEntry.attributeEditPermissions().get("k")).isEmpty();
        assertThatThrownBy(() -> propsVerifier.verify(emptySnapshot))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("realmName must not be blank")
                .hasMessageContaining("accessTokenLifespan");

        // Null nested mapper/client collections & non-OIDC enabled IdP + keycloak-oidc missing flow/empty flow
        IdpContract samlIdp = new IdpContract("saml-idp", "saml", true, null, null, null);
        IdpContract kcOidcMissingFlow = new IdpContract(null, "keycloak-oidc", true, "missing-flow", "https://sso.example.invalid/realms/x", null);
        IdpContract kcOidcEmptyFlow = new IdpContract("kc-empty", "keycloak-oidc", true, "empty-flow", "https://sso.example.invalid/realms/x", null);
        Map<String, List<String>> flowsWithNullAndEmpty = new java.util.LinkedHashMap<>();
        flowsWithNullAndEmpty.put("empty-flow", List.of());
        flowsWithNullAndEmpty.put("null-flow", null);

        KeycloakRealmContractSnapshot base = validSnapshot();
        KeycloakRealmContractSnapshot edgeIdpSnapshot = new KeycloakRealmContractSnapshot(
                "master", base.enabled(), true, true, false, true, "en", 0,
                Set.of("offline_access"), List.of(samlIdp, kcOidcMissingFlow, kcOidcEmptyFlow),
                flowsWithNullAndEmpty, new UserProfileContract("ADMIN_EDIT", Map.of("awgment_status", Set.of("admin"))),
                List.of(new ClientContract(null, true, false, true, false, false,
                        List.of(new ProtocolMapperContract("m", "other-mapper", null))))
        );
        assertThatThrownBy(() -> propsVerifier.verify(edgeIdpSnapshot))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("master")
                .hasMessageContaining("empty-flow")
                .hasMessageContaining("existing firstBrokerLoginFlowAlias");
    }

    private static KeycloakRealmContractSnapshot validSnapshot() {
        return new KeycloakRealmContractSnapshot(
                "acme",
                true,
                true,
                false,
                false,
                true,
                "en",
                300,
                Set.of("offline_access"),
                List.of(new IdpContract(
                        "entra-acme",
                        "oidc",
                        true,
                        "first-broker-login-no-email-link",
                        "http://keycloak:8080/realms/mock-entra",
                        List.of(new IdpMapperContract(
                                "map-entra-tid-to-session-note",
                                "oidc-user-session-note-idp-mapper",
                                Map.of("session.note", "idp_tenant_id", "claim", "tid")
                        ))
                )),
                Map.of("first-broker-login-no-email-link", List.of("idp-create-user-if-unique")),
                new UserProfileContract(null, validAttributeEditPermissions()),
                List.of(
                        new ClientContract(
                                "awgment-web",
                                true,
                                false,
                                true,
                                true,
                                false,
                                List.of(
                                        new ProtocolMapperContract(
                                                "awgment-account-audience",
                                                "oidc-audience-mapper",
                                                Map.of("included.client.audience", EXPECTED_AUDIENCE, "access.token.claim", "true")
                                        ),
                                        new ProtocolMapperContract(
                                                "identity-provider-session-note",
                                                "oidc-usersessionmodel-note-mapper",
                                                Map.of(
                                                        "user.session.note", "identity_provider",
                                                        "claim.name", "identity_provider",
                                                        "access.token.claim", "true"
                                                )
                                        ),
                                        new ProtocolMapperContract(
                                                "idp-tenant-id-session-note",
                                                "oidc-usersessionmodel-note-mapper",
                                                Map.of(
                                                        "user.session.note", "idp_tenant_id",
                                                        "claim.name", "idp_tenant_id",
                                                        "access.token.claim", "true"
                                                )
                                        ),
                                        new ProtocolMapperContract(
                                                "awgment-status-attribute",
                                                "oidc-usermodel-attribute-mapper",
                                                Map.of(
                                                        "user.attribute", "awgment_status",
                                                        "claim.name", "awgment_status",
                                                        "access.token.claim", "true"
                                                )
                                        ),
                                        new ProtocolMapperContract(
                                                "awgment-user-id-attribute",
                                                "oidc-usermodel-attribute-mapper",
                                                Map.of(
                                                        "user.attribute", "userId",
                                                        "claim.name", "userId",
                                                        "access.token.claim", "true"
                                                )
                                        )
                                )
                        ),
                        new ClientContract(
                                "awgment-service-client",
                                true,
                                false,
                                false,
                                false,
                                true,
                                List.of(new ProtocolMapperContract(
                                        "awgment-account-audience",
                                        "oidc-audience-mapper",
                                        Map.of("included.client.audience", EXPECTED_AUDIENCE, "access.token.claim", "true")
                                ))
                        )
                )
        );
    }

    private static Map<String, Set<String>> validAttributeEditPermissions() {
        return Map.of(
                "awgment_status", Set.of("admin"),
                "userId", Set.of("admin"),
                "awgment_sync_version", Set.of("admin"),
                "locale", Set.of("admin")
        );
    }

    private static KeycloakRealmContractSnapshot withIdps(
            KeycloakRealmContractSnapshot base,
            List<IdpContract> idps,
            Map<String, List<String>> flows) {
        return new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), idps,
                flows, base.userProfile(), base.clients()
        );
    }

    private static KeycloakRealmContractSnapshot withUserProfile(
            KeycloakRealmContractSnapshot base,
            UserProfileContract userProfile) {
        return new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), userProfile, base.clients()
        );
    }

    private static KeycloakRealmContractSnapshot withClients(
            KeycloakRealmContractSnapshot base,
            List<ClientContract> clients) {
        return new KeycloakRealmContractSnapshot(
                base.realmName(), base.enabled(), base.verifyEmail(), base.registrationAllowed(),
                base.duplicateEmailsAllowed(), base.internationalizationEnabled(), base.defaultLocale(),
                base.accessTokenLifespan(), base.defaultRoleComposites(), base.identityProviders(),
                base.authFlowExecutions(), base.userProfile(), clients
        );
    }
}
