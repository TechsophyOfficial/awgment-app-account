package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.IdentityProvidersResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleByIdResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserProfileResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakAdminRealmClientTest {

    @Test
    void propertiesShouldHaveExpectedDefaultsAndAllowOverrides() {
        KeycloakAdminProperties props = new KeycloakAdminProperties();
        assertThat(props.getServerUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.getRealm()).isEqualTo("master");
        assertThat(props.getClientId()).isEqualTo("admin-cli");
        assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(8000);

        props.setServerUrl("http://keycloak:8080/");
        props.setRealm("platform");
        props.setClientId("awgment-admin-sync");
        props.setClientSecret("secret-val");
        props.setUsername("admin");
        props.setPassword("pw");
        props.setConnectTimeoutMs(3000);
        props.setReadTimeoutMs(4000);

        assertThat(props.getServerUrl()).isEqualTo("http://keycloak:8080/");
        assertThat(props.getRealm()).isEqualTo("platform");
        assertThat(props.getClientId()).isEqualTo("awgment-admin-sync");
        assertThat(props.getClientSecret()).isEqualTo("secret-val");
        assertThat(props.getUsername()).isEqualTo("admin");
        assertThat(props.getPassword()).isEqualTo("pw");
        assertThat(props.getConnectTimeoutMs()).isEqualTo(3000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(4000);
    }

    @Test
    void fetchRealmContractShouldAssembleFullSnapshotAndFilterDisabledExecutions() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        when(keycloak.realm("acme")).thenReturn(realmResource);

        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("acme");
        realmRep.setEnabled(true);
        realmRep.setVerifyEmail(true);
        realmRep.setRegistrationAllowed(false);
        realmRep.setDuplicateEmailsAllowed(false);
        realmRep.setInternationalizationEnabled(true);
        realmRep.setDefaultLocale("en");
        realmRep.setAccessTokenLifespan(300);
        RoleRepresentation defaultRole = new RoleRepresentation("default-roles-acme", "default", true);
        realmRep.setDefaultRole(defaultRole);
        when(realmResource.toRepresentation()).thenReturn(realmRep);

        RolesResource rolesResource = mock(RolesResource.class);
        RoleResource defaultRoleResource = mock(RoleResource.class);
        RoleByIdResource roleByIdResource = mock(RoleByIdResource.class);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("default-roles-acme")).thenReturn(defaultRoleResource);
        when(realmResource.rolesById()).thenReturn(roleByIdResource);

        RoleRepresentation nestedParent = new RoleRepresentation("parent-role", "p", false);
        nestedParent.setId("role-parent-1");
        nestedParent.setComposite(true);
        RoleRepresentation childRole = new RoleRepresentation("awgment-user", "c", false);
        childRole.setId("role-child-1");
        when(defaultRoleResource.getRoleComposites()).thenReturn(Set.of(nestedParent));
        when(roleByIdResource.getRoleComposites("role-parent-1")).thenReturn(Set.of(childRole));

        IdentityProvidersResource idpsResource = mock(IdentityProvidersResource.class);
        IdentityProviderResource idpResource = mock(IdentityProviderResource.class);
        when(realmResource.identityProviders()).thenReturn(idpsResource);
        IdentityProviderRepresentation idpRep = new IdentityProviderRepresentation();
        idpRep.setAlias("entra-acme");
        idpRep.setProviderId("oidc");
        idpRep.setEnabled(true);
        idpRep.setFirstBrokerLoginFlowAlias("first-broker-login-no-email-link");
        idpRep.setConfig(Map.of("issuer", "http://keycloak:8080/realms/mock-entra"));
        when(idpsResource.findAll()).thenReturn(List.of(idpRep));
        when(idpsResource.get("entra-acme")).thenReturn(idpResource);

        IdentityProviderMapperRepresentation idpMapper = new IdentityProviderMapperRepresentation();
        idpMapper.setName("map-tid");
        idpMapper.setIdentityProviderMapper("oidc-user-session-note-idp-mapper");
        idpMapper.setConfig(Map.of("session.note", "idp_tenant_id"));
        when(idpResource.getMappers()).thenReturn(List.of(idpMapper));

        AuthenticationManagementResource flowsResource = mock(AuthenticationManagementResource.class);
        when(realmResource.flows()).thenReturn(flowsResource);
        AuthenticationFlowRepresentation flowRep = new AuthenticationFlowRepresentation();
        flowRep.setAlias("first-broker-login-no-email-link");
        when(flowsResource.getFlows()).thenReturn(List.of(flowRep));

        AuthenticationExecutionInfoRepresentation activeExec = new AuthenticationExecutionInfoRepresentation();
        activeExec.setProviderId("idp-create-user-if-unique");
        activeExec.setRequirement("REQUIRED");
        AuthenticationExecutionInfoRepresentation disabledExec = new AuthenticationExecutionInfoRepresentation();
        disabledExec.setProviderId("idp-auto-link");
        disabledExec.setRequirement("DISABLED");
        when(flowsResource.getExecutions("first-broker-login-no-email-link"))
                .thenReturn(List.of(activeExec, disabledExec));

        UsersResource usersResource = mock(UsersResource.class);
        UserProfileResource userProfileResource = mock(UserProfileResource.class);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.userProfile()).thenReturn(userProfileResource);
        UPConfig upConfig = new UPConfig();
        upConfig.setUnmanagedAttributePolicy(UPConfig.UnmanagedAttributePolicy.ADMIN_EDIT);
        upConfig.setAttributes(List.of(
                new UPAttribute("awgment_status", new UPAttributePermissions(Set.of("admin", "user"), Set.of("admin"))),
                new UPAttribute("userId", new UPAttributePermissions(Set.of("admin"), Set.of("admin"))),
                new UPAttribute("awgment_sync_version", new UPAttributePermissions(Set.of("admin"), Set.of("admin"))),
                new UPAttribute("locale", new UPAttributePermissions(Set.of("admin"), Set.of("admin")))
        ));
        when(userProfileResource.getConfiguration()).thenReturn(upConfig);

        ClientsResource clientsResource = mock(ClientsResource.class);
        ClientResource webClientResource = mock(ClientResource.class);
        ProtocolMappersResource protocolMappersResource = mock(ProtocolMappersResource.class);
        when(realmResource.clients()).thenReturn(clientsResource);

        ClientRepresentation webClient = new ClientRepresentation();
        webClient.setId("client-uuid-1");
        webClient.setClientId("awgment-web");
        webClient.setEnabled(true);
        webClient.setBearerOnly(false);
        webClient.setStandardFlowEnabled(true);
        webClient.setDirectAccessGrantsEnabled(true);
        webClient.setServiceAccountsEnabled(false);
        webClient.setProtocolMappers(null); // triggers fallback fetch via ClientResource

        ProtocolMapperRepresentation audMapper = new ProtocolMapperRepresentation();
        audMapper.setName("aud");
        audMapper.setProtocolMapper("oidc-audience-mapper");
        audMapper.setConfig(Map.of("included.client.audience", "awgment-account", "access.token.claim", "true"));

        when(clientsResource.findAll()).thenReturn(List.of(webClient));
        when(clientsResource.get("client-uuid-1")).thenReturn(webClientResource);
        when(webClientResource.getProtocolMappers()).thenReturn(protocolMappersResource);
        when(protocolMappersResource.getMappers()).thenReturn(List.of(audMapper));

        KeycloakAdminProperties props = new KeycloakAdminProperties();
        props.setClientSecret("secret");
        KeycloakAdminRealmClient client = new KeycloakAdminRealmClient(props, p -> keycloak);

        KeycloakRealmContractSnapshot snapshot = client.fetchRealmContract("acme");

        assertThat(snapshot.realmName()).isEqualTo("acme");
        assertThat(snapshot.accessTokenLifespan()).isEqualTo(300);
        assertThat(snapshot.defaultRoleComposites()).containsExactlyInAnyOrder("parent-role", "awgment-user");
        assertThat(snapshot.authFlowExecutions().get("first-broker-login-no-email-link"))
                .containsExactly("idp-create-user-if-unique");
        assertThat(snapshot.userProfile().unmanagedAttributePolicy()).isEqualTo("ADMIN_EDIT");
        assertThat(snapshot.clients()).hasSize(1);
        assertThat(snapshot.clients().getFirst().protocolMappers()).hasSize(1);
        verify(keycloak).close();
    }

    @Test
    void setRealmEnabledShouldUpdateRepresentationAndWrapUpstreamErrors() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        when(keycloak.realm("acme")).thenReturn(realmResource);
        RealmRepresentation rep = new RealmRepresentation();
        rep.setEnabled(true);
        when(realmResource.toRepresentation()).thenReturn(rep);

        KeycloakAdminRealmClient client = new KeycloakAdminRealmClient(new KeycloakAdminProperties(), p -> keycloak);
        client.setRealmEnabled("acme", false);

        assertThat(rep.isEnabled()).isFalse();
        verify(realmResource).update(any(RealmRepresentation.class));
        verify(keycloak).close();

        when(keycloak.realm("missing")).thenThrow(new NotFoundException("Realm missing"));
        assertThatThrownBy(() -> client.fetchRealmContract("missing"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        assertThatThrownBy(() -> client.setRealmEnabled(" ", true))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        assertThatThrownBy(() -> client.fetchRealmContract(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));
    }

    @Test
    void fetchRealmContractShouldHandleEmptyAndNullSubResourcesAndBuildDefaultClients() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        when(keycloak.realm("acme")).thenReturn(realmResource);

        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm("acme");
        rep.setEnabled(true);
        rep.setDefaultRole(null);
        when(realmResource.toRepresentation()).thenReturn(rep);

        IdentityProvidersResource idps = mock(IdentityProvidersResource.class);
        when(realmResource.identityProviders()).thenReturn(idps);
        when(idps.findAll()).thenReturn(List.of());

        AuthenticationManagementResource flows = mock(AuthenticationManagementResource.class);
        when(realmResource.flows()).thenReturn(flows);
        AuthenticationFlowRepresentation blankFlow = new AuthenticationFlowRepresentation();
        blankFlow.setAlias(" ");
        when(flows.getFlows()).thenReturn(List.of(blankFlow));

        UsersResource users = mock(UsersResource.class);
        UserProfileResource userProfile = mock(UserProfileResource.class);
        when(realmResource.users()).thenReturn(users);
        when(users.userProfile()).thenReturn(userProfile);
        when(userProfile.getConfiguration()).thenReturn(null);

        ClientsResource clients = mock(ClientsResource.class);
        when(realmResource.clients()).thenReturn(clients);
        ClientRepresentation populatedClient = new ClientRepresentation();
        populatedClient.setClientId("awgment-web");
        populatedClient.setEnabled(true);
        populatedClient.setBearerOnly(false);
        populatedClient.setStandardFlowEnabled(true);
        populatedClient.setDirectAccessGrantsEnabled(false);
        populatedClient.setServiceAccountsEnabled(false);
        ProtocolMapperRepresentation pm = new ProtocolMapperRepresentation();
        pm.setName("aud");
        pm.setProtocolMapper("oidc-audience-mapper");
        pm.setConfig(null);
        populatedClient.setProtocolMappers(List.of(pm));
        when(clients.findAll()).thenReturn(List.of(populatedClient));

        KeycloakAdminRealmClient client = new KeycloakAdminRealmClient(new KeycloakAdminProperties(), p -> keycloak);
        KeycloakRealmContractSnapshot snapshot = client.fetchRealmContract("acme");
        assertThat(snapshot.defaultRoleComposites()).isEmpty();
        assertThat(snapshot.identityProviders()).isEmpty();
        assertThat(snapshot.authFlowExecutions()).isEmpty();
        assertThat(snapshot.userProfile()).isNull();
        assertThat(snapshot.clients()).hasSize(1);
        assertThat(snapshot.clients().getFirst().protocolMappers().getFirst().config()).isEmpty();

        // Null representation throws ApiException
        when(realmResource.toRepresentation()).thenReturn(null);
        assertThatThrownBy(() -> client.fetchRealmContract("acme"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        // setRealmEnabled wraps unexpected RuntimeException
        when(realmResource.toRepresentation()).thenThrow(new IllegalStateException("upstream reset"));
        assertThatThrownBy(() -> client.setRealmEnabled("acme", true))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        // Exercise buildDefaultClient for both client_credentials and password grants without network I/O
        KeycloakAdminProperties secretProps = new KeycloakAdminProperties();
        secretProps.setClientSecret("secret");
        try (Keycloak kcSecret = KeycloakAdminRealmClient.buildDefaultClient(secretProps)) {
            assertThat(kcSecret.isClosed()).isFalse();
        }
        KeycloakAdminProperties pwProps = new KeycloakAdminProperties();
        pwProps.setUsername("admin");
        pwProps.setPassword("pw");
        try (Keycloak kcPassword = KeycloakAdminRealmClient.buildDefaultClient(pwProps)) {
            assertThat(kcPassword.isClosed()).isFalse();
        }
    }

    @Test
    void fetchRealmContractShouldHandleNullSubResourceListsAndRethrowApiExceptions() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        when(keycloak.realm("acme")).thenReturn(realmResource);

        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm("acme");
        rep.setEnabled(true);
        rep.setDefaultRole(new RoleRepresentation("default-roles-acme", "d", false));
        when(realmResource.toRepresentation()).thenReturn(rep);

        RolesResource rolesResource = mock(RolesResource.class);
        RoleResource defaultRoleRes = mock(RoleResource.class);
        RoleByIdResource roleById = mock(RoleByIdResource.class);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("default-roles-acme")).thenReturn(defaultRoleRes);
        when(realmResource.rolesById()).thenReturn(roleById);

        RoleRepresentation unnamedComposite = new RoleRepresentation(null, "u", false);
        unnamedComposite.setId("comp-1");
        unnamedComposite.setComposite(true);
        when(defaultRoleRes.getRoleComposites()).thenReturn(Set.of(unnamedComposite));
        when(roleById.getRoleComposites("comp-1")).thenReturn(null);

        IdentityProvidersResource idps = mock(IdentityProvidersResource.class);
        IdentityProviderResource idpRes = mock(IdentityProviderResource.class);
        when(realmResource.identityProviders()).thenReturn(idps);
        IdentityProviderRepresentation idpNoCfg = new IdentityProviderRepresentation();
        idpNoCfg.setAlias("ext");
        idpNoCfg.setConfig(null);
        when(idps.findAll()).thenReturn(List.of(idpNoCfg));
        when(idps.get("ext")).thenReturn(idpRes);
        when(idpRes.getMappers()).thenReturn(null);

        AuthenticationManagementResource flows = mock(AuthenticationManagementResource.class);
        when(realmResource.flows()).thenReturn(flows);
        AuthenticationFlowRepresentation flow = new AuthenticationFlowRepresentation();
        flow.setAlias("flow-null-exec");
        when(flows.getFlows()).thenReturn(List.of(flow));
        when(flows.getExecutions("flow-null-exec")).thenReturn(null);

        UsersResource users = mock(UsersResource.class);
        UserProfileResource upRes = mock(UserProfileResource.class);
        when(realmResource.users()).thenReturn(users);
        when(users.userProfile()).thenReturn(upRes);
        UPConfig upConfig = new UPConfig();
        UPAttribute nullNameAttr = new UPAttribute(null);
        UPAttribute nullPermsAttr = new UPAttribute("locale");
        nullPermsAttr.setPermissions(null);
        UPAttribute nullEditAttr = new UPAttribute("userId");
        nullEditAttr.setPermissions(new UPAttributePermissions(Set.of(), null));
        upConfig.setAttributes(List.of(nullNameAttr, nullPermsAttr, nullEditAttr));
        when(upRes.getConfiguration()).thenReturn(upConfig);

        ClientsResource clients = mock(ClientsResource.class);
        when(realmResource.clients()).thenReturn(clients);
        when(clients.findAll()).thenReturn(null);

        KeycloakAdminRealmClient client = new KeycloakAdminRealmClient(new KeycloakAdminProperties(), p -> keycloak);
        KeycloakRealmContractSnapshot snapshot = client.fetchRealmContract("acme");
        assertThat(snapshot.defaultRoleComposites()).isEmpty();
        assertThat(snapshot.identityProviders()).hasSize(1);
        assertThat(snapshot.identityProviders().getFirst().issuer()).isNull();
        assertThat(snapshot.authFlowExecutions().get("flow-null-exec")).isEmpty();
        assertThat(snapshot.userProfile().attributeEditPermissions().get("locale")).isEmpty();
        assertThat(snapshot.userProfile().attributeEditPermissions().get("userId")).isEmpty();
        assertThat(snapshot.clients()).isEmpty();

        // Also cover null idps/flows/defaultComposites/upAttributes & ApiException passthrough
        when(defaultRoleRes.getRoleComposites()).thenReturn(null);
        when(idps.findAll()).thenReturn(null);
        when(flows.getFlows()).thenReturn(null);
        upConfig.setAttributes(null);
        KeycloakRealmContractSnapshot snapshot2 = client.fetchRealmContract("acme");
        assertThat(snapshot2.identityProviders()).isEmpty();
        assertThat(snapshot2.authFlowExecutions()).isEmpty();

        when(realmResource.toRepresentation())
                .thenThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "direct api ex"));
        assertThatThrownBy(() -> client.fetchRealmContract("acme"))
                .isInstanceOf(ApiException.class)
                .hasMessage("direct api ex");
        assertThatThrownBy(() -> client.setRealmEnabled("acme", true))
                .isInstanceOf(ApiException.class)
                .hasMessage("direct api ex");
    }

    @Test
    void fetchRealmContractShouldHandleClientAndRoleAndFlowEdgeBranches() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        when(keycloak.realm("acme")).thenReturn(realmResource);

        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm("acme");
        rep.setEnabled(true);
        rep.setDefaultRole(new RoleRepresentation(null, "d", false));
        when(realmResource.toRepresentation()).thenReturn(rep);

        IdentityProvidersResource idps = mock(IdentityProvidersResource.class);
        IdentityProviderResource idpRes = mock(IdentityProviderResource.class);
        when(realmResource.identityProviders()).thenReturn(idps);
        IdentityProviderRepresentation idpWithNullCfgMapper = new IdentityProviderRepresentation();
        idpWithNullCfgMapper.setAlias("ext");
        idpWithNullCfgMapper.setConfig(null);
        IdentityProviderMapperRepresentation nullCfgMapper = new IdentityProviderMapperRepresentation();
        nullCfgMapper.setName("m1");
        nullCfgMapper.setConfig(null);
        when(idps.findAll()).thenReturn(List.of(idpWithNullCfgMapper));
        when(idps.get("ext")).thenReturn(idpRes);
        when(idpRes.getMappers()).thenReturn(List.of(nullCfgMapper));

        AuthenticationManagementResource flows = mock(AuthenticationManagementResource.class);
        when(realmResource.flows()).thenReturn(flows);
        AuthenticationFlowRepresentation nullAliasFlow = new AuthenticationFlowRepresentation();
        nullAliasFlow.setAlias(null);
        AuthenticationFlowRepresentation flowWithEdgeExecs = new AuthenticationFlowRepresentation();
        flowWithEdgeExecs.setAlias("edge-flow");
        when(flows.getFlows()).thenReturn(List.of(nullAliasFlow, flowWithEdgeExecs));
        AuthenticationExecutionInfoRepresentation nullPid = new AuthenticationExecutionInfoRepresentation();
        nullPid.setRequirement("REQUIRED");
        nullPid.setProviderId(null);
        AuthenticationExecutionInfoRepresentation blankPid = new AuthenticationExecutionInfoRepresentation();
        blankPid.setRequirement("REQUIRED");
        blankPid.setProviderId(" ");
        when(flows.getExecutions("edge-flow")).thenReturn(List.of(nullPid, blankPid));

        UsersResource users = mock(UsersResource.class);
        UserProfileResource upRes = mock(UserProfileResource.class);
        when(realmResource.users()).thenReturn(users);
        when(users.userProfile()).thenReturn(upRes);
        when(upRes.getConfiguration()).thenReturn(null);

        ClientsResource clients = mock(ClientsResource.class);
        when(realmResource.clients()).thenReturn(clients);
        ClientRepresentation disabledClient = new ClientRepresentation();
        disabledClient.setClientId("disabled");
        disabledClient.setEnabled(false);
        disabledClient.setId("c1");
        ClientRepresentation bearerClient = new ClientRepresentation();
        bearerClient.setClientId("awgment-account");
        bearerClient.setEnabled(true);
        bearerClient.setBearerOnly(true);
        bearerClient.setId("c2");
        ClientRepresentation builtinClient = new ClientRepresentation();
        builtinClient.setClientId("account-console");
        builtinClient.setEnabled(true);
        builtinClient.setBearerOnly(false);
        builtinClient.setId("c3");
        ClientRepresentation noIdClient = new ClientRepresentation();
        noIdClient.setClientId("custom-no-id");
        noIdClient.setEnabled(true);
        noIdClient.setBearerOnly(false);
        noIdClient.setId(null);
        noIdClient.setProtocolMappers(List.of());
        ClientRepresentation emptyMapperClient = new ClientRepresentation();
        emptyMapperClient.setClientId("custom-empty");
        emptyMapperClient.setEnabled(true);
        emptyMapperClient.setBearerOnly(false);
        emptyMapperClient.setId("c4");
        emptyMapperClient.setProtocolMappers(List.of());
        ClientResource emptyClientRes = mock(ClientResource.class);
        ProtocolMappersResource emptyPmsRes = mock(ProtocolMappersResource.class);
        when(clients.findAll()).thenReturn(List.of(disabledClient, bearerClient, builtinClient, noIdClient, emptyMapperClient));
        when(clients.get("c4")).thenReturn(emptyClientRes);
        when(emptyClientRes.getProtocolMappers()).thenReturn(emptyPmsRes);
        when(emptyPmsRes.getMappers()).thenReturn(null);

        KeycloakAdminRealmClient client = new KeycloakAdminRealmClient(new KeycloakAdminProperties(), p -> keycloak);
        KeycloakRealmContractSnapshot s1 = client.fetchRealmContract("acme");
        assertThat(s1.identityProviders().getFirst().mappers()).hasSize(1);
        assertThat(s1.clients()).hasSize(5);

        // Blank default role name + composite without ID + duplicate visited composite
        rep.setDefaultRole(new RoleRepresentation(" ", "d", false));
        assertThat(client.fetchRealmContract("acme").defaultRoleComposites()).isEmpty();

        rep.setDefaultRole(new RoleRepresentation("default-roles-acme", "d", false));
        RolesResource rolesRes = mock(RolesResource.class);
        RoleResource defRoleRes = mock(RoleResource.class);
        RoleByIdResource byIdRes = mock(RoleByIdResource.class);
        when(realmResource.roles()).thenReturn(rolesRes);
        when(rolesRes.get("default-roles-acme")).thenReturn(defRoleRes);
        when(realmResource.rolesById()).thenReturn(byIdRes);
        RoleRepresentation compNoId = new RoleRepresentation("no-id-comp", "d", false);
        compNoId.setComposite(true);
        compNoId.setId(null);
        RoleRepresentation compCycle = new RoleRepresentation("cycle", "d", false);
        compCycle.setComposite(true);
        compCycle.setId("cycle-id");
        when(defRoleRes.getRoleComposites()).thenReturn(Set.of(compNoId, compCycle));
        when(byIdRes.getRoleComposites("cycle-id")).thenReturn(Set.of(compCycle));

        KeycloakRealmContractSnapshot s2 = client.fetchRealmContract("acme");
        assertThat(s2.defaultRoleComposites()).containsExactlyInAnyOrder("no-id-comp", "cycle");
    }
}
