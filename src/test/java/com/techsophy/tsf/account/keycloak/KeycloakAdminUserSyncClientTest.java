package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.outbox.OutboxProperties;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.UserStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleByIdResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakAdminUserSyncClientTest {

    private KeycloakAdminProperties adminProperties;
    private AwgmentSecurityProperties securityProperties;
    private OutboxProperties outboxProperties;
    private SimpleMeterRegistry meterRegistry;

    private Keycloak keycloak;
    private RealmResource realmResource;
    private UsersResource usersResource;
    private ClientsResource clientsResource;
    private ClientResource trustedClientResource;
    private RolesResource realmRolesResource;
    private RolesResource trustedClientRolesResource;
    private RoleByIdResource roleByIdResource;

    @BeforeEach
    void setUp() {
        adminProperties = new KeycloakAdminProperties();
        adminProperties.setServerUrl("http://localhost:8080");
        adminProperties.setClientSecret("sync-secret");
        adminProperties.setConnectTimeoutMs(9000);
        adminProperties.setReadTimeoutMs(9500);

        securityProperties = new AwgmentSecurityProperties();
        securityProperties.setTrustedClientId("awgment-account");

        outboxProperties = new OutboxProperties();
        outboxProperties.setSyncRevokeTimeoutMs(1000L);
        meterRegistry = new SimpleMeterRegistry();

        keycloak = mock(Keycloak.class);
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        clientsResource = mock(ClientsResource.class);
        trustedClientResource = mock(ClientResource.class);
        realmRolesResource = mock(RolesResource.class);
        trustedClientRolesResource = mock(RolesResource.class);
        roleByIdResource = mock(RoleByIdResource.class);

        when(keycloak.realm("acme")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(realmResource.clients()).thenReturn(clientsResource);
        when(realmResource.roles()).thenReturn(realmRolesResource);
        when(realmResource.rolesById()).thenReturn(roleByIdResource);

        ClientRepresentation trustedClientRep = new ClientRepresentation();
        trustedClientRep.setId("client-uuid-awgment");
        trustedClientRep.setClientId("awgment-account");
        when(clientsResource.findByClientId("awgment-account")).thenReturn(List.of(trustedClientRep));
        when(clientsResource.get("client-uuid-awgment")).thenReturn(trustedClientResource);
        when(trustedClientResource.roles()).thenReturn(trustedClientRolesResource);
    }

    @Test
    void shouldClampTimeoutsBelowLeaseTtlAndCloseSharedClient() {
        KeycloakAdminProperties oversized = new KeycloakAdminProperties();
        oversized.setServerUrl("");
        oversized.setConnectTimeoutMs(50 * 1000);
        oversized.setReadTimeoutMs(60 * 1000);

        AtomicReference<KeycloakAdminProperties> capturedProps = new AtomicReference<>();
        KeycloakAdminUserSyncClient client = new KeycloakAdminUserSyncClient(
                oversized,
                securityProperties,
                outboxProperties,
                meterRegistry,
                props -> {
                    capturedProps.set(props);
                    return keycloak;
                },
                true
        );

        when(keycloak.isClosed()).thenReturn(false);
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of());
        Response createdResp = Response.created(URI.create("http://localhost:8080/admin/realms/acme/users/sub-new")).build();
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(createdResp);
        UserResource createdUserRes = mock(UserResource.class);
        RoleMappingResource roleMappingRes = mockRoleMappings(List.of(), List.of());
        when(usersResource.get("sub-new")).thenReturn(createdUserRes);
        when(createdUserRes.roles()).thenReturn(roleMappingRes);

        UserReconcilePayload payload = new UserReconcilePayload(
                101L, null, "alice", "alice" + "@" + "acme.invalid",
                "Alice", "Smith", "fr", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL,
                null, 1L
        );
        ReconcileResult result = client.reconcileUser("acme", payload);
        assertThat(result.keycloakSub()).isEqualTo("sub-new");
        assertThat(result.fenced()).isFalse();

        assertThat(capturedProps.get().getConnectTimeoutMs()).isLessThanOrEqualTo(9500);
        assertThat(capturedProps.get().getReadTimeoutMs()).isLessThanOrEqualTo(9500);
        assertThat(capturedProps.get().getServerUrl()).isEqualTo("http://localhost:8080");

        client.close();
        verify(keycloak).close();
    }

    @Test
    void reconcileUserShouldCreateWithoutPasswordsAndConvergeRolesWithoutStrippingDefaults() {
        KeycloakAdminUserSyncClient client = newClient(false);
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of());
        Response createdResp = Response.created(URI.create("http://localhost:8080/admin/realms/acme/users/kc-uuid-1")).build();
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(createdResp);

        RoleRepresentation defaultRole = role("r-def", "default-roles-acme", false);
        RoleRepresentation awgUser = role("r-usr", "awgment-user", false);
        RoleRepresentation awgRead = role("r-read", "awgment-account-read", false);
        RoleRepresentation staleRole = role("r-old", "awgment-account-delete", false);
        when(realmRolesResource.list()).thenReturn(List.of(defaultRole));
        when(trustedClientRolesResource.list()).thenReturn(List.of(awgUser, awgRead, staleRole));

        UserResource userRes = mock(UserResource.class);
        RoleMappingResource mappingRes = mockRoleMappings(List.of(defaultRole), List.of(staleRole));
        when(usersResource.get("kc-uuid-1")).thenReturn(userRes);
        when(userRes.roles()).thenReturn(mappingRes);

        UserReconcilePayload payload = new UserReconcilePayload(
                501L, null, "alice", "alice" + "@" + "acme.invalid",
                "Alice", "Liddell", "ja", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL,
                List.of("awgment-user", "awgment-account-read"), 2L
        );

        ReconcileResult result = client.reconcileUser("acme", payload);
        assertThat(result).isEqualTo(new ReconcileResult("kc-uuid-1", false));

        ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        UserRepresentation created = userCaptor.getValue();
        assertThat(created.getCredentials()).isNull();
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getUsername()).isEqualTo("alice");
        assertThat(created.getEmail()).isEqualTo("alice" + "@" + "acme.invalid");
        assertThat(created.getAttributes()).containsEntry("awgment_status", List.of("ACTIVE"));
        assertThat(created.getAttributes()).containsEntry("userId", List.of("501"));
        assertThat(created.getAttributes()).containsEntry("awgment_sync_version", List.of("2"));
        assertThat(created.getAttributes()).containsEntry("locale", List.of("ja"));

        verify(mappingRes.clientLevel("client-uuid-awgment")).add(List.of(awgUser, awgRead));
        verify(mappingRes.clientLevel("client-uuid-awgment")).remove(List.of(staleRole));
        verify(mappingRes.realmLevel(), never()).remove(anyList());
    }

    @Test
    void reconcileUserShouldFenceStaleSyncVersionPreserveUnmanagedAttributesAndLogoutDisabledUsers() {
        KeycloakAdminUserSyncClient client = newClient(false);

        UserRepresentation existing = new UserRepresentation();
        existing.setId("kc-existing-1");
        existing.setUsername("bob");
        existing.setEnabled(true);
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("awgment_sync_version", List.of("5"));
        attrs.put("unmanaged_legacy", List.of("keep-me"));
        existing.setAttributes(attrs);

        UserResource userRes = mock(UserResource.class);
        when(usersResource.get("kc-existing-1")).thenReturn(userRes);
        when(userRes.toRepresentation()).thenReturn(existing);

        // Stale v4 payload must be fenced without calling update or logout
        UserReconcilePayload stalePayload = new UserReconcilePayload(
                502L, "kc-existing-1", "bob", "bob" + "@" + "acme.invalid",
                "Bob", "Builder", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL,
                null, 4L
        );
        ReconcileResult fenced = client.reconcileUser("acme", stalePayload);
        assertThat(fenced).isEqualTo(new ReconcileResult("kc-existing-1", true));
        verify(userRes, never()).update(any());
        verify(userRes, never()).logout();

        // Newer v6 DISABLED payload for EXTERNAL_IDP updates attributes without altering IdP profile fields and calls logout
        existing.setFirstName("IdP First");
        existing.setEmail("idp" + "@" + "acme.invalid");
        UserReconcilePayload disablePayload = new UserReconcilePayload(
                502L, "kc-existing-1", "bob", "override" + "@" + "acme.invalid",
                "Override", "Name", null, UserStatus.DISABLED, IdentitySource.EXTERNAL_IDP,
                null, 6L
        );
        ReconcileResult updated = client.reconcileUser("acme", disablePayload);
        assertThat(updated).isEqualTo(new ReconcileResult("kc-existing-1", false));
        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getFirstName()).isEqualTo("IdP First");
        assertThat(existing.getEmail()).isEqualTo("idp" + "@" + "acme.invalid");
        assertThat(existing.getAttributes()).containsEntry("unmanaged_legacy", List.of("keep-me"));
        assertThat(existing.getAttributes()).containsEntry("awgment_sync_version", List.of("6"));
        assertThat(existing.getAttributes()).containsEntry("awgment_status", List.of("DISABLED"));
        assertThat(existing.getAttributes()).doesNotContainKey("locale");
        verify(userRes).update(existing);
        verify(userRes).logout();
    }

    @Test
    void revokeUserSynchronouslyShouldHandleSuccessFenceBlankSubAndFailuresWithMetric() {
        KeycloakAdminUserSyncClient client = newClient(false);

        assertThat(client.revokeUserSynchronously("acme", "  ", 2L, "DISABLED")).isFalse();
        assertThat(meterRegistry.counter("awgment_revocation_sync_failed_total", "tenant", "acme").count()).isZero();

        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-1");
        rep.setEnabled(true);
        rep.setAttributes(new HashMap<>(Map.of("awgment_sync_version", List.of("1"))));
        UserResource userRes = mock(UserResource.class);
        when(usersResource.get("kc-1")).thenReturn(userRes);
        when(userRes.toRepresentation()).thenReturn(rep);

        assertThat(client.revokeUserSynchronously("acme", "kc-1", 2L, "DISABLED")).isTrue();
        assertThat(rep.isEnabled()).isFalse();
        assertThat(rep.getAttributes()).containsEntry("awgment_status", List.of("DISABLED"));
        assertThat(rep.getAttributes()).containsEntry("awgment_sync_version", List.of("2"));
        verify(userRes).update(rep);
        verify(userRes).logout();

        // Timeout / exception increments Micrometer counter and returns false
        doAnswer(inv -> {
            Thread.sleep(1500);
            return rep;
        }).when(userRes).toRepresentation();
        assertThat(client.revokeUserSynchronously("acme", "kc-1", 3L, "DISABLED")).isFalse();
        assertThat(meterRegistry.counter("awgment_revocation_sync_failed_total", "tenant", "acme").count())
                .isEqualTo(1.0);

        when(usersResource.get("kc-err")).thenThrow(new RuntimeException("KC connection refused"));
        assertThat(client.revokeUserSynchronously("acme", "kc-err", 3L, "REJECTED")).isFalse();
        assertThat(meterRegistry.counter("awgment_revocation_sync_failed_total", "tenant", "acme").count())
                .isEqualTo(2.0);

        // Rep == null returns false; kcVersion > syncVersion returns true without updating
        UserResource nullRepRes = mock(UserResource.class);
        when(usersResource.get("kc-null")).thenReturn(nullRepRes);
        when(nullRepRes.toRepresentation()).thenReturn(null);
        assertThat(client.revokeUserSynchronously("acme", "kc-null", 2L, "DISABLED")).isFalse();

        UserRepresentation newerRep = new UserRepresentation();
        newerRep.setId("kc-newer");
        newerRep.setAttributes(Map.of("awgment_sync_version", List.of("9")));
        UserResource newerRes = mock(UserResource.class);
        when(usersResource.get("kc-newer")).thenReturn(newerRes);
        when(newerRes.toRepresentation()).thenReturn(newerRep);
        assertThat(client.revokeUserSynchronously("acme", "kc-newer", 5L, "DISABLED")).isTrue();
        verify(newerRes, never()).update(any());
    }

    @Test
    void shouldHandle409RaceFallbackRealmRoleDiffActionEmailsAndBoundedClientBuild() {
        KeycloakAdminUserSyncClient client = newClient(false);

        // 1. 409 conflict on create falls back to searchByUsername and converges realm roles
        when(usersResource.searchByUsername("raceuser", true))
                .thenReturn(List.of())
                .thenReturn(List.of(userRep("kc-race", "raceuser", "abc")));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());

        RoleRepresentation defaultRole = role("r-def", "default-roles-acme", false);
        RoleRepresentation offlineRole = role("r-off", "offline_access", false);
        RoleRepresentation customRealmKeep = role("r-keep", "custom-realm-role", false);
        RoleRepresentation customRealmRemove = role("r-rem", "old-realm-role", false);
        when(realmRolesResource.list()).thenReturn(List.of(defaultRole, offlineRole, customRealmKeep, customRealmRemove));
        when(trustedClientRolesResource.list()).thenReturn(List.of());

        UserResource raceRes = mock(UserResource.class);
        RoleMappingResource raceMappings = mockRoleMappings(
                List.of(defaultRole, offlineRole, customRealmRemove),
                List.of()
        );
        when(usersResource.get("kc-race")).thenReturn(raceRes);
        when(raceRes.roles()).thenReturn(raceMappings);

        UserReconcilePayload racePayload = new UserReconcilePayload(
                901L, null, "raceuser", "raceuser" + "@" + "acme.invalid",
                "Race", "User", "en", UserStatus.REJECTED, IdentitySource.KEYCLOAK_LOCAL,
                List.of("custom-realm-role"), 1L
        );
        ReconcileResult raceResult = client.reconcileUser("acme", racePayload);
        assertThat(raceResult).isEqualTo(new ReconcileResult("kc-race", false));
        verify(raceMappings.realmLevel()).add(List.of(customRealmKeep));
        verify(raceMappings.realmLevel()).remove(List.of(customRealmRemove));
        verify(raceRes).logout();

        // 2. Non-2xx/non-409 status on create throws UPSTREAM_IDENTITY_PROVIDER_ERROR
        when(usersResource.searchByUsername("erruser", true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.serverError().build());
        UserReconcilePayload errPayload = new UserReconcilePayload(
                902L, null, "erruser", "erruser" + "@" + "acme.invalid",
                "Err", "User", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL,
                null, 1L
        );
        assertThatThrownBy(() -> client.reconcileUser("acme", errPayload))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        // 3. sendActionEmail validation + happy path + error wrapping
        assertThatThrownBy(() -> client.sendActionEmail("acme", " ", List.of("VERIFY_EMAIL"), 3600))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> client.sendActionEmail("acme", "kc-1", List.of(), 3600))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        UserResource emailRes = mock(UserResource.class);
        when(usersResource.get("kc-email")).thenReturn(emailRes);
        client.sendActionEmail("acme", "kc-email", List.of("UPDATE_PASSWORD"), 1800);
        verify(emailRes).executeActionsEmail(List.of("UPDATE_PASSWORD"), 1800);

        doAnswer(inv -> {
            throw new RuntimeException("SMTP failure");
        }).when(emailRes).executeActionsEmail(anyList(), any(Integer.class));
        assertThatThrownBy(() -> client.sendActionEmail("acme", "kc-email", List.of("UPDATE_PASSWORD"), 1800))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        // 4. buildBoundedClient (secret & password grants) + close without init + internal base URL fallback
        KeycloakAdminProperties secretProps = new KeycloakAdminProperties();
        secretProps.setServerUrl("http://localhost:8080");
        secretProps.setRealm("master");
        secretProps.setClientId("awgment-admin-sync");
        secretProps.setClientSecret("secret");
        secretProps.setConnectTimeoutMs(50 * 1000);
        secretProps.setReadTimeoutMs(10);
        try (Keycloak builtSecret = KeycloakAdminUserSyncClient.buildBoundedClient(secretProps)) {
            assertThat(builtSecret).isNotNull();
        }

        KeycloakAdminProperties passwordProps = new KeycloakAdminProperties();
        passwordProps.setServerUrl("http://localhost:8080");
        passwordProps.setRealm("master");
        passwordProps.setClientId("admin-cli");
        passwordProps.setClientSecret(null);
        passwordProps.setUsername("admin");
        passwordProps.setPassword("pw");
        try (Keycloak builtPassword = KeycloakAdminUserSyncClient.buildBoundedClient(passwordProps)) {
            assertThat(builtPassword).isNotNull();
        }

        // Null securityProperties + public URL fallback + shared client reuse
        AwgmentSecurityProperties publicOnlyProps = new AwgmentSecurityProperties();
        publicOnlyProps.setKeycloakBaseUrl("http://localhost:8080/");
        KeycloakAdminProperties blankUrl = new KeycloakAdminProperties();
        blankUrl.setServerUrl(null);
        KeycloakAdminUserSyncClient publicFallbackClient = new KeycloakAdminUserSyncClient(
                blankUrl, publicOnlyProps, outboxProperties, meterRegistry, p -> keycloak, true);
        when(keycloak.isClosed()).thenReturn(true, false);
        assertThat(publicFallbackClient.fetchUserEffectiveRoles("acme", null)).isEmpty();
        publicFallbackClient.close();

        AwgmentSecurityProperties blankClientIdProps = new AwgmentSecurityProperties();
        blankClientIdProps.setTrustedClientId("   ");
        KeycloakAdminUserSyncClient nullSecClient = new KeycloakAdminUserSyncClient(
                blankUrl, null, outboxProperties, meterRegistry, p -> keycloak, false);
        KeycloakAdminUserSyncClient blankClientIdSync = new KeycloakAdminUserSyncClient(
                blankUrl, blankClientIdProps, outboxProperties, meterRegistry, p -> keycloak, true);
        when(keycloak.isClosed()).thenReturn(false);
        assertThat(blankClientIdSync.revokeUserSynchronously("acme", null, 1L, "DISABLED")).isFalse();
        assertThat(nullSecClient.expandCompositeClosure("acme", java.util.Arrays.asList(null, " ", "custom-a")))
                .contains("custom-a");
        assertThatThrownBy(() -> nullSecClient.expandCompositeClosure(" ", List.of("a")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> nullSecClient.sendActionEmail("acme", null, List.of("a"), 3600))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> nullSecClient.sendActionEmail("acme", "kc-1", null, 3600))
                .isInstanceOf(ApiException.class);

        // Revoke with null attributes + InterruptedException path
        UserRepresentation noAttrRevokeRep = new UserRepresentation();
        noAttrRevokeRep.setId("kc-rev-noattr");
        noAttrRevokeRep.setAttributes(null);
        UserResource revNoAttrRes = mock(UserResource.class);
        when(usersResource.get("kc-rev-noattr")).thenReturn(revNoAttrRes);
        when(revNoAttrRes.toRepresentation()).thenReturn(noAttrRevokeRep);
        assertThat(client.revokeUserSynchronously("acme", "kc-rev-noattr", 2L, "DISABLED")).isTrue();

        // ClientMappings present without trustedClientId & null ClientMappings + null RoleRepresentation
        UserResource otherClientRes = mock(UserResource.class);
        RoleMappingResource otherClientMap = mock(RoleMappingResource.class);
        MappingsRepresentation otherMappings = new MappingsRepresentation();
        otherMappings.setRealmMappings(java.util.Arrays.asList((RoleRepresentation) null));
        ClientMappingsRepresentation otherCm = new ClientMappingsRepresentation();
        otherCm.setClient("other");
        otherCm.setMappings(List.of());
        otherMappings.setClientMappings(Map.of("other", otherCm));
        when(usersResource.get("kc-other-client")).thenReturn(otherClientRes);
        when(otherClientRes.roles()).thenReturn(otherClientMap);
        when(otherClientMap.getAll()).thenReturn(otherMappings);
        assertThat(client.fetchUserEffectiveRoles("acme", "kc-other-client")).isEmpty();

        MappingsRepresentation nullCmMappings = new MappingsRepresentation();
        when(otherClientMap.getAll()).thenReturn(nullCmMappings);
        assertThat(client.fetchUserEffectiveRoles("acme", "kc-other-client")).isEmpty();

        // Empty/mismatched searchByUsername + blank username + null rolesResource/clientsResource + null role names
        when(usersResource.searchByUsername("mismatch", true))
                .thenReturn(java.util.Arrays.asList(null, userRep("id-1", null, "1"), userRep("id-2", "other", "1")));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(400).build());
        UserReconcilePayload mismatchPayload = new UserReconcilePayload(
                907L, null, "mismatch", "m" + "@" + "acme.invalid",
                "M", "M", "en", null, IdentitySource.KEYCLOAK_LOCAL, List.of(), 1L);
        assertThatThrownBy(() -> client.reconcileUser("acme", mismatchPayload))
                .isInstanceOf(ApiException.class);

        UserReconcilePayload blankUsernamePayload = new UserReconcilePayload(
                908L, null, " ", "b" + "@" + "acme.invalid",
                "B", "B", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL, null, 1L);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(
                Response.created(URI.create("http://localhost:8080/admin/realms/acme/users/kc-blank-u")).build());
        UserResource blankURes = mock(UserResource.class);
        when(usersResource.get("kc-blank-u")).thenReturn(blankURes);
        assertThat(client.reconcileUser("acme", blankUsernamePayload)).isEqualTo(new ReconcileResult("kc-blank-u", false));

        // convergeRoles with null-name current role, protected uma_authorization, blank desiredRole, null clientsResource
        RoleRepresentation nullNameRole = new RoleRepresentation();
        RoleRepresentation umaRole = role("r-uma", "uma_authorization", false);
        RoleRepresentation clientNullName = new RoleRepresentation();
        UserResource roleEdgeRes = mock(UserResource.class);
        RoleMappingResource edgeRoleMap = mockRoleMappings(
                List.of(umaRole, nullNameRole),
                List.of(clientNullName)
        );
        ClientRepresentation trustedRep = new ClientRepresentation();
        trustedRep.setId("client-uuid-awgment");
        when(clientsResource.findByClientId("awgment-account")).thenReturn(List.of(trustedRep));
        when(trustedClientResource.roles()).thenReturn(null);
        when(realmRolesResource.list()).thenReturn(java.util.Arrays.asList(null, role("r-b", " ", false)));
        when(usersResource.get("kc-role-edge")).thenReturn(roleEdgeRes);
        when(roleEdgeRes.toRepresentation()).thenReturn(userRep("kc-role-edge", "edge", "0"));
        when(roleEdgeRes.roles()).thenReturn(edgeRoleMap);
        UserReconcilePayload edgeRolesPayload = new UserReconcilePayload(
                909L, "kc-role-edge", "edge", "e" + "@" + "acme.invalid",
                "E", "D", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL,
                List.of(" ", "unknown-role", "default-roles-acme"), 1L);
        assertThat(client.reconcileUser("acme", edgeRolesPayload)).isEqualTo(new ReconcileResult("kc-role-edge", false));

        when(realmResource.clients()).thenReturn(null);
        when(realmResource.roles()).thenReturn(null);
        assertThat(client.expandCompositeClosure("acme", List.of("a"))).containsExactly("a");
        // Restore clients/roles mocks for remaining assertions
        when(realmResource.clients()).thenReturn(clientsResource);
        when(realmResource.roles()).thenReturn(realmRolesResource);
        when(trustedClientResource.roles()).thenReturn(trustedClientRolesResource);

        // 409 conflict where follow-up lookup returns empty -> UPSTREAM_IDENTITY_PROVIDER_ERROR
        when(usersResource.searchByUsername("ghost409", true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());
        UserReconcilePayload ghostPayload = new UserReconcilePayload(
                904L, null, "ghost409", "ghost" + "@" + "acme.invalid",
                "G", "H", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL, null, 1L);
        assertThatThrownBy(() -> client.reconcileUser("acme", ghostPayload))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        // Create DISABLED user triggers logout; missing trusted client & null roleMappings handled safely
        when(usersResource.searchByUsername("disnew", true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(
                Response.created(URI.create("http://localhost:8080/admin/realms/acme/users/kc-dis-new")).build());
        UserResource disNewRes = mock(UserResource.class);
        when(usersResource.get("kc-dis-new")).thenReturn(disNewRes);
        when(disNewRes.roles()).thenReturn(null);
        UserReconcilePayload disNewPayload = new UserReconcilePayload(
                905L, null, "disnew", "disnew" + "@" + "acme.invalid",
                "Dis", "New", "en", UserStatus.DISABLED, IdentitySource.KEYCLOAK_LOCAL, List.of("awgment-user"), 1L);
        assertThat(client.reconcileUser("acme", disNewPayload)).isEqualTo(new ReconcileResult("kc-dis-new", false));
        verify(disNewRes).logout();

        // fetchUserEffectiveRoles null mappings + runtime exception + expandCompositeClosure null directRoles
        assertThat(client.expandCompositeClosure("acme", null)).isEmpty();
        UserResource emptyMapRes = mock(UserResource.class);
        RoleMappingResource emptyRoleMap = mock(RoleMappingResource.class);
        when(usersResource.get("kc-empty-map")).thenReturn(emptyMapRes);
        when(emptyMapRes.roles()).thenReturn(emptyRoleMap);
        when(emptyRoleMap.getAll()).thenReturn(null);
        assertThat(client.fetchUserEffectiveRoles("acme", "kc-empty-map")).isEmpty();

        when(emptyRoleMap.getAll()).thenThrow(new RuntimeException("KC 500"));
        assertThatThrownBy(() -> client.fetchUserEffectiveRoles("acme", "kc-empty-map"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        // Missing sub attribute & null attributes coverage on existing user
        UserRepresentation noAttrUser = new UserRepresentation();
        noAttrUser.setId("kc-no-attr");
        noAttrUser.setUsername("noattr");
        noAttrUser.setAttributes(null);
        UserResource noAttrRes = mock(UserResource.class);
        when(usersResource.get("kc-no-attr")).thenReturn(noAttrRes);
        when(noAttrRes.toRepresentation()).thenReturn(noAttrUser);
        when(clientsResource.findByClientId("awgment-account")).thenReturn(List.of());
        UserReconcilePayload noClientPayload = new UserReconcilePayload(
                906L, "kc-no-attr", "noattr", "noattr" + "@" + "acme.invalid",
                "No", "Attr", " ", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL, List.of("awgment-user"), 1L);
        assertThat(client.reconcileUser("acme", noClientPayload)).isEqualTo(new ReconcileResult("kc-no-attr", false));

        AwgmentSecurityProperties internalProps = new AwgmentSecurityProperties();
        internalProps.setKeycloakInternalBaseUrl("http://keycloak:8080/");
        KeycloakAdminProperties blankUrlProps = new KeycloakAdminProperties();
        blankUrlProps.setServerUrl("");
        KeycloakAdminUserSyncClient unusedShared = new KeycloakAdminUserSyncClient(
                blankUrlProps, internalProps, outboxProperties, null, p -> keycloak, true);
        // Calls effectiveBoundedProperties() with internal base URL and null meterRegistry before close()
        assertThat(unusedShared.revokeUserSynchronously("acme", "kc-null", 1L, "DISABLED")).isFalse();
        unusedShared.close();

        // NotFoundException on keycloakSub falls back to username lookup; UserReconcilePayload null syncVersion defaults to 1L
        UserResource missingSubRes = mock(UserResource.class);
        when(usersResource.get("stale-sub")).thenReturn(missingSubRes);
        when(missingSubRes.toRepresentation()).thenThrow(new NotFoundException("deleted"));
        UserRepresentation foundByUsername = userRep("kc-recovered", "recoverme", "0");
        when(usersResource.searchByUsername("recoverme", true)).thenReturn(List.of(foundByUsername));
        UserResource recoveredRes = mock(UserResource.class);
        when(usersResource.get("kc-recovered")).thenReturn(recoveredRes);

        com.techsophy.tsf.account.user.UserEntity entity = new com.techsophy.tsf.account.user.UserEntity();
        entity.setId(903L);
        entity.setKeycloakSub("stale-sub");
        entity.setUserName("recoverme");
        entity.setEmailId("recoverme" + "@" + "acme.invalid");
        entity.setStatus(UserStatus.ACTIVE);
        entity.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
        entity.setSyncVersion(null);
        UserReconcilePayload fromEntity = UserReconcilePayload.from(entity);
        assertThat(fromEntity.syncVersion()).isEqualTo(1L);
        assertThat(client.reconcileUser("acme", fromEntity)).isEqualTo(new ReconcileResult("kc-recovered", false));
    }

    @Test
    void shouldHandleNullSubLookupNullListsEmptySyncVersionAndNullRolesById() {
        KeycloakAdminUserSyncClient client = newClient(false);

        // Expand composite containing a client role (clientRole != null) when rolesById is null
        RoleRepresentation clientComposite = role("id-cc", "awgment-account-admin", true);
        when(trustedClientRolesResource.list()).thenReturn(List.of(clientComposite));
        when(realmRolesResource.list()).thenReturn(null);
        when(realmResource.rolesById()).thenReturn(null);
        assertThat(client.expandCompositeClosure("acme", List.of("awgment-account-admin")))
                .containsExactly("awgment-account-admin");
        when(realmResource.rolesById()).thenReturn(roleByIdResource);

        // toRepresentation returns null -> falls back to searchByUsername returning null -> creates user
        UserResource nullSubRepRes = mock(UserResource.class);
        when(usersResource.get("sub-null-rep")).thenReturn(nullSubRepRes);
        when(nullSubRepRes.toRepresentation()).thenReturn(null);
        when(usersResource.searchByUsername("fallbackuser", true)).thenReturn(null);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(
                Response.created(URI.create("http://localhost:8080/admin/realms/acme/users/kc-created-fb")).build());
        UserResource createdFbRes = mock(UserResource.class);
        when(usersResource.get("kc-created-fb")).thenReturn(createdFbRes);
        when(clientsResource.findByClientId("awgment-account")).thenReturn(null);

        UserReconcilePayload fbPayload = new UserReconcilePayload(
                910L, "sub-null-rep", "fallbackuser", "fb" + "@" + "acme.invalid",
                "F", "B", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL, List.of(), 1L);
        assertThat(client.reconcileUser("acme", fbPayload)).isEqualTo(new ReconcileResult("kc-created-fb", false));

        // Null userName fallback + null ClientRepresentation list + null listAll + empty/blank sync version attrs
        UserRepresentation blankVerUser = new UserRepresentation();
        blankVerUser.setId("kc-blank-ver");
        blankVerUser.setAttributes(Map.of());
        UserResource blankVerRes = mock(UserResource.class);
        RoleMappingResource nullListsMapping = mock(RoleMappingResource.class);
        RoleScopeResource nullRealmScope = mock(RoleScopeResource.class);
        RoleScopeResource nullClientScope = mock(RoleScopeResource.class);
        when(nullListsMapping.realmLevel()).thenReturn(nullRealmScope);
        when(nullRealmScope.listAll()).thenReturn(null);
        when(nullListsMapping.clientLevel("cid-null-res")).thenReturn(nullClientScope);
        when(nullClientScope.listAll()).thenReturn(null);
        when(usersResource.get("kc-blank-ver")).thenReturn(blankVerRes);
        when(blankVerRes.toRepresentation()).thenReturn(blankVerUser);
        when(blankVerRes.roles()).thenReturn(nullListsMapping);
        when(clientsResource.findByClientId("awgment-account"))
                .thenReturn(java.util.Arrays.asList((ClientRepresentation) null));

        UserReconcilePayload nullUserPayload = new UserReconcilePayload(
                911L, "kc-blank-ver", null, "nullu" + "@" + "acme.invalid",
                "N", "U", "en", UserStatus.ACTIVE, IdentitySource.KEYCLOAK_LOCAL, List.of(), 1L);
        assertThat(client.reconcileUser("acme", nullUserPayload)).isEqualTo(new ReconcileResult("kc-blank-ver", false));

        // Empty list & blank string in awgment_sync_version attributes + null clientResource
        blankVerUser.setAttributes(Map.of("awgment_sync_version", List.of()));
        ClientRepresentation validRep = new ClientRepresentation();
        validRep.setId("cid-null-res");
        when(clientsResource.findByClientId("awgment-account")).thenReturn(List.of(validRep));
        when(clientsResource.get("cid-null-res")).thenReturn(null);
        assertThat(client.reconcileUser("acme", nullUserPayload)).isEqualTo(new ReconcileResult("kc-blank-ver", false));

        blankVerUser.setAttributes(Map.of("awgment_sync_version", List.of("   ")));
        assertThat(client.reconcileUser("acme", nullUserPayload)).isEqualTo(new ReconcileResult("kc-blank-ver", false));
    }

    private static UserRepresentation userRep(String id, String username, String syncVer) {
        UserRepresentation u = new UserRepresentation();
        u.setId(id);
        u.setUsername(username);
        u.setAttributes(new HashMap<>(Map.of("awgment_sync_version", List.of(syncVer))));
        return u;
    }

    @Test
    void expandCompositeClosureAndFetchUserEffectiveRolesShouldBeCycleSafeAndFailClosedOnOutage() {
        KeycloakAdminUserSyncClient client = newClient(false);

        RoleRepresentation roleA = role("id-a", "custom-a", true);
        RoleRepresentation roleB = role("id-b", "custom-b", true);
        RoleRepresentation tier1 = role("id-admin", "awgment-account-admin", false);
        RoleRepresentation viewer = role("id-view", "viewer", false);

        when(realmRolesResource.list()).thenReturn(List.of(roleA, roleB));
        when(trustedClientRolesResource.list()).thenReturn(List.of(tier1));
        when(roleByIdResource.getRoleComposites("id-a")).thenReturn(Set.of(roleB));
        when(roleByIdResource.getRoleComposites("id-b")).thenReturn(Set.of(roleA, tier1));

        Set<String> expanded = client.expandCompositeClosure("acme", List.of("custom-a", "unknown-app-role"));
        assertThat(expanded).containsExactlyInAnyOrder("custom-a", "custom-b", "awgment-account-admin", "unknown-app-role");

        // User effective roles combines realm + trustedClient + composite closure
        UserResource userRes = mock(UserResource.class);
        RoleMappingResource mappingRes = mock(RoleMappingResource.class);
        MappingsRepresentation allMappings = new MappingsRepresentation();
        allMappings.setRealmMappings(List.of(roleA));
        ClientMappingsRepresentation clientMappings = new ClientMappingsRepresentation();
        clientMappings.setClient("awgment-account");
        clientMappings.setMappings(List.of(viewer));
        allMappings.setClientMappings(Map.of("awgment-account", clientMappings));
        when(usersResource.get("kc-1")).thenReturn(userRes);
        when(userRes.roles()).thenReturn(mappingRes);
        when(mappingRes.getAll()).thenReturn(allMappings);

        Set<String> effective = client.fetchUserEffectiveRoles("acme", "kc-1");
        assertThat(effective).containsExactlyInAnyOrder("custom-a", "custom-b", "awgment-account-admin", "viewer");
        assertThat(client.fetchUserEffectiveRoles("acme", " ")).isEmpty();
        assertThat(client.expandCompositeClosure("acme", List.of())).isEmpty();

        // Missing user in Keycloak returns empty set cleanly
        when(usersResource.get("kc-missing")).thenThrow(new NotFoundException("missing"));
        assertThat(client.fetchUserEffectiveRoles("acme", "kc-missing")).isEmpty();

        // Keycloak outage throws UPSTREAM_IDENTITY_PROVIDER_ERROR
        when(realmRolesResource.list()).thenThrow(new RuntimeException("KC 503"));
        assertThatThrownBy(() -> client.expandCompositeClosure("acme", List.of("custom-a")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));
    }

    private KeycloakAdminUserSyncClient newClient(boolean reuseShared) {
        return new KeycloakAdminUserSyncClient(
                adminProperties,
                securityProperties,
                outboxProperties,
                meterRegistry,
                p -> keycloak,
                reuseShared
        );
    }

    private static RoleRepresentation role(String id, String name, boolean composite) {
        RoleRepresentation r = new RoleRepresentation(name, name, composite);
        r.setId(id);
        r.setComposite(composite);
        return r;
    }

    private static RoleMappingResource mockRoleMappings(
            List<RoleRepresentation> realmRoles,
            List<RoleRepresentation> clientRoles) {
        RoleMappingResource mappingRes = mock(RoleMappingResource.class);
        RoleScopeResource realmScope = mock(RoleScopeResource.class);
        RoleScopeResource clientScope = mock(RoleScopeResource.class);
        when(mappingRes.realmLevel()).thenReturn(realmScope);
        when(mappingRes.clientLevel("client-uuid-awgment")).thenReturn(clientScope);
        when(realmScope.listAll()).thenReturn(realmRoles);
        when(clientScope.listAll()).thenReturn(clientRoles);
        return mappingRes;
    }
}
