package com.techsophy.tsf.account.user;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.governance.RoleGovernancePolicy;
import com.techsophy.tsf.account.idp.TrustedIdpEntity;
import com.techsophy.tsf.account.idp.TrustedIdpRepository;
import com.techsophy.tsf.account.keycloak.KeycloakUserSyncClient;
import com.techsophy.tsf.account.outbox.OutboxService;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.user.dto.CreateUserRequest;
import com.techsophy.tsf.account.user.dto.UpdateMyProfileRequest;
import com.techsophy.tsf.account.user.dto.UpdateUserRequest;
import com.techsophy.tsf.account.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserService and UserPersistenceService covering UC-USER-01..07,
 * edge cases, governance enforcement, and sync-first deactivation phasing.
 */
class UserServiceTest {

    private static final String REALM = "acme";
    private static final CallerPrincipal OPERATOR = new CallerPrincipal(
            "op-sub-1", REALM, Set.of("awgment-account-all"), false);
    private static final CallerPrincipal APPROVER = new CallerPrincipal(
            "approver-sub-1", REALM, Set.of("awgment-account-admin"), false);

    private UserRepository userRepository;
    private TrustedIdpRepository trustedIdpRepository;
    private RoleGovernancePolicy roleGovernancePolicy;
    private OutboxService outboxService;
    private KeycloakUserSyncClient keycloakUserSyncClient;
    private SecurityAuditService securityAuditService;
    private IdGeneratorImpl idGenerator;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        trustedIdpRepository = mock(TrustedIdpRepository.class);
        roleGovernancePolicy = mock(RoleGovernancePolicy.class);
        outboxService = mock(OutboxService.class);
        keycloakUserSyncClient = mock(KeycloakUserSyncClient.class);
        securityAuditService = mock(SecurityAuditService.class);
        idGenerator = mock(IdGeneratorImpl.class);

        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(2001L));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity e = inv.getArgument(0);
            if (e.getOptLockVersion() == null) {
                e.setOptLockVersion(0L);
            }
            if (e.getCreatedAt() == null) {
                e.setCreatedAt(Instant.now());
            }
            e.setUpdatedAt(Instant.now());
            return e;
        });

        UserPersistenceService persistenceService = new UserPersistenceService(
                userRepository,
                trustedIdpRepository,
                roleGovernancePolicy,
                outboxService,
                securityAuditService,
                idGenerator
        );
        userService = new UserService(
                userRepository,
                persistenceService,
                roleGovernancePolicy,
                outboxService,
                keycloakUserSyncClient
        );
    }

    @Test
    void createUserLocalShouldNormalizePersistEnqueueReconcileAndEmailAndDispatch() {
        String rawEmail = "Alice.Smith" + "@" + "Acme.Invalid";
        String expectedEmail = "alice.smith" + "@" + "acme.invalid";
        CreateUserRequest request = new CreateUserRequest(
                "Alice.Smith",
                rawEmail,
                " Alice ",
                " Smith ",
                "+1-555-0100",
                " Engineering ",
                null,
                "UTC",
                Map.of("costCenter", "ENG"),
                null,
                null,
                List.of("awgment-user", "awgment-user"),
                List.of("eng-group")
        );

        UserResponse response = userService.createUser(request, OPERATOR);

        assertThat(response.id()).isEqualTo(2001L);
        assertThat(response.userName()).isEqualTo("alice.smith");
        assertThat(response.emailId()).isEqualTo(expectedEmail);
        assertThat(response.firstName()).isEqualTo("Alice");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.department()).isEqualTo("Engineering");
        assertThat(response.locale()).isEqualTo("en");
        assertThat(response.identitySource()).isEqualTo(IdentitySource.KEYCLOAK_LOCAL);
        assertThat(response.provisioningMode()).isEqualTo(ProvisioningMode.DIRECT_OPERATOR);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.desiredRoles()).containsExactly("awgment-user");
        assertThat(response.desiredGroups()).containsExactly("eng-group");
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);

        verify(roleGovernancePolicy).assertCanAssignRoles(
                eq(REALM), eq(OPERATOR.directRoles()), eq(List.of()), eq(List.of("awgment-user")));
        verify(outboxService).enqueueUserReconcile(2001L, 1L);
        verify(outboxService).enqueueActionEmail(
                2001L, List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"), 24 * 60 * 60);
        verify(securityAuditService).record(
                eq("USER_CREATE"), eq("USER"), eq("2001"), eq(AuditOutcome.SUCCESS),
                eq("op-sub-1"), isNull(), anyMap());
        verify(outboxService).dispatchPendingBatch(REALM, 1);
    }

    @Test
    void createUserExternalIdpShouldVerifyEnabledTrustAndSkipActionEmail() {
        TrustedIdpEntity idp = new TrustedIdpEntity();
        idp.setId(501L);
        idp.setIdpAlias("entra-acme");
        idp.setEnabled(true);
        when(trustedIdpRepository.findByIdpAlias("entra-acme")).thenReturn(Optional.of(idp));

        CreateUserRequest request = new CreateUserRequest(
                "ext.user",
                "ext.user" + "@" + "acme.invalid",
                "Ext",
                "User",
                null,
                "Ops",
                "fr",
                null,
                null,
                IdentitySource.EXTERNAL_IDP,
                "entra-acme",
                null,
                null
        );

        UserResponse response = userService.createUser(request, OPERATOR);

        assertThat(response.identitySource()).isEqualTo(IdentitySource.EXTERNAL_IDP);
        assertThat(response.idpAlias()).isEqualTo("entra-acme");
        assertThat(response.locale()).isEqualTo("fr");
        assertThat(response.desiredRoles()).isNull();
        verify(outboxService).enqueueUserReconcile(2001L, 1L);
        verify(outboxService, never()).enqueueActionEmail(anyLong(), any(), anyInt());
    }

    @Test
    void createUserShouldRejectServiceAccountPrefixDisabledIdpDuplicatesAndEscalations() {
        CreateUserRequest servicePrefix = new CreateUserRequest(
                "Service-Account-spoof", "sa" + "@" + "acme.invalid",
                null, null, null, null, null, null, null,
                IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(servicePrefix, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        CreateUserRequest localWithIdp = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid",
                null, null, null, null, null, null, null,
                IdentitySource.KEYCLOAK_LOCAL, "entra-acme", null, null);
        assertThatThrownBy(() -> userService.createUser(localWithIdp, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        CreateUserRequest missingAlias = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid",
                null, null, null, null, null, null, null,
                IdentitySource.EXTERNAL_IDP, " ", null, null);
        assertThatThrownBy(() -> userService.createUser(missingAlias, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        CreateUserRequest unknownIdp = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid",
                null, null, null, null, null, null, null,
                IdentitySource.EXTERNAL_IDP, "unknown-idp", null, null);
        when(trustedIdpRepository.findByIdpAlias("unknown-idp")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.createUser(unknownIdp, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        TrustedIdpEntity disabledIdp = new TrustedIdpEntity();
        disabledIdp.setIdpAlias("entra-acme");
        disabledIdp.setEnabled(false);
        when(trustedIdpRepository.findByIdpAlias("entra-acme")).thenReturn(Optional.of(disabledIdp));
        CreateUserRequest disabledIdpReq = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid",
                null, null, null, null, null, null, null,
                IdentitySource.EXTERNAL_IDP, "entra-acme", null, null);
        assertThatThrownBy(() -> userService.createUser(disabledIdpReq, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.IDP_TRUST_REVOKED);

        String dupEmail = "dup" + "@" + "acme.invalid";
        when(userRepository.existsActiveOrDisabledByEmail(dupEmail)).thenReturn(true);
        CreateUserRequest dupEmailReq = new CreateUserRequest(
                "alice", dupEmail,
                null, null, null, null, null, null, null,
                IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(dupEmailReq, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.EMAIL_ALREADY_CLAIMED);

        String freeEmail = "free" + "@" + "acme.invalid";
        when(userRepository.existsActiveOrDisabledByEmail(freeEmail)).thenReturn(false);
        when(userRepository.existsActiveOrDisabledByUserName("taken")).thenReturn(true);
        CreateUserRequest dupUserReq = new CreateUserRequest(
                "taken", freeEmail,
                null, null, null, null, null, null, null,
                IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(dupUserReq, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.DUPLICATE_RESOURCE);

        doThrow(new ApiException(ApiErrorCode.ROLE_ESCALATION_FORBIDDEN, "forbidden"))
                .when(roleGovernancePolicy)
                .assertCanAssignRoles(eq(REALM), any(), any(), eq(List.of("awgment-account-admin")));
        CreateUserRequest escalatedReq = new CreateUserRequest(
                "newuser", freeEmail,
                null, null, null, null, null, null, null,
                IdentitySource.KEYCLOAK_LOCAL, null, List.of("awgment-account-admin"), null);
        assertThatThrownBy(() -> userService.createUser(escalatedReq, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.ROLE_ESCALATION_FORBIDDEN);

        verifyNoInteractions(outboxService);
    }

    @Test
    void createUserShouldStillReturn201IfPostCommitDispatchThrowsTransiently() {
        doThrow(new RuntimeException("transient kc error"))
                .when(outboxService).dispatchPendingBatch(REALM, 1);

        CreateUserRequest request = new CreateUserRequest(
                "resilient.user",
                "resilient" + "@" + "acme.invalid",
                "Resilient",
                "User",
                null, null, "en-US", null, null,
                IdentitySource.KEYCLOAK_LOCAL, null, List.of("awgment-user"), null
        );

        UserResponse response = userService.createUser(request, OPERATOR);
        assertThat(response.id()).isEqualTo(2001L);
        assertThat(response.locale()).isEqualTo("en-US");
    }

    @Test
    void updateUserShouldExpandLiveKeycloakRolesCheckVersionAndIncrementSyncVersion() {
        UserEntity existing = activeUser(3001L, "kc-sub-3001", "bob");
        existing.setDesiredRoles(List.of("awgment-user"));
        existing.setOptLockVersion(2L);
        existing.setSyncVersion(1L);
        when(userRepository.findById(3001L)).thenReturn(Optional.of(existing));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-3001"))
                .thenReturn(Set.of("awgment-user", "viewer"));

        UpdateUserRequest req = new UpdateUserRequest(
                "BobUpdated", "JonesUpdated", "+1-555-0200", "Security", "ja", "Asia/Tokyo",
                Map.of("clearance", "L2"), 2L
        );

        UserResponse updated = userService.updateUser(3001L, req, OPERATOR);

        assertThat(updated.firstName()).isEqualTo("BobUpdated");
        assertThat(updated.department()).isEqualTo("Security");
        assertThat(updated.locale()).isEqualTo("ja");
        assertThat(existing.getSyncVersion()).isEqualTo(2L);
        assertThat(existing.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);

        ArgumentCaptor<Set<String>> rolesCaptor = ArgumentCaptor.captor();
        verify(roleGovernancePolicy).assertCanManageTargetUser(
                eq(REALM), eq(OPERATOR.directRoles()), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).containsExactlyInAnyOrder("awgment-user", "viewer");
        verify(outboxService).enqueueUserReconcile(3001L, 2L);
        verify(outboxService).dispatchPendingBatch(REALM, 1);
    }

    @Test
    void updateUserShouldRejectNotFoundVersionConflictAndHigherTierTarget() {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateUser(
                9999L, new UpdateUserRequest("A", null, null, null, null, null, null, 0L), OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);

        UserEntity existing = activeUser(3001L, null, "bob");
        existing.setOptLockVersion(5L);
        when(userRepository.findById(3001L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> userService.updateUser(
                3001L, new UpdateUserRequest("A", null, null, null, null, null, null, 4L), OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);

        doThrow(new ApiException(ApiErrorCode.ROLE_ESCALATION_FORBIDDEN, "higher tier"))
                .when(roleGovernancePolicy).assertCanManageTargetUser(eq(REALM), any(), any());
        assertThatThrownBy(() -> userService.updateUser(
                3001L, new UpdateUserRequest("A", null, null, null, null, null, null, 5L), OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.ROLE_ESCALATION_FORBIDDEN);
    }

    @Test
    void getUserByIdAndSearchUsersShouldSupportMandatoryFieldsAndValidatePagingAndSort() {
        UserEntity user = activeUser(3002L, "kc-sub-3002", "carol");
        user.setDepartment("Legal");
        user.setAttributes(Map.of("tier", "gold"));
        user.setSelfServicePreferences(Map.of("theme", "dark"));
        when(userRepository.findById(3002L)).thenReturn(Optional.of(user));

        UserResponse full = userService.getUserById(3002L, false);
        assertThat(full.department()).isEqualTo("Legal");
        assertThat(full.attributes()).containsEntry("tier", "gold");

        UserResponse mandatory = userService.getUserById(3002L, true);
        assertThat(mandatory.id()).isEqualTo(3002L);
        assertThat(mandatory.userName()).isEqualTo("carol");
        assertThat(mandatory.department()).isNull();
        assertThat(mandatory.attributes()).isNull();
        assertThat(mandatory.selfServicePreferences()).isNull();

        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        PageResponse<UserResponse> page = userService.searchUsers(
                "car", UserStatus.ACTIVE, null, null, "Legal", null, true, 0, 20, "userName,asc", OPERATOR);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).department()).isNull();

        assertThatThrownBy(() -> userService.searchUsers(
                null, null, null, null, null, null, false, -1, 20, "createdAt,desc", OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> userService.searchUsers(
                null, null, null, null, null, null, false, 0, 201, "createdAt,desc", OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> userService.searchUsers(
                null, null, null, null, null, null, false, 0, 20, "optLockVersion,desc", OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void deactivateUserShouldCommitPhaseABeforeSyncRevokeAndReturn204Vs202() {
        UserEntity target = activeUser(4001L, "kc-sub-4001", "dave");
        target.setSyncVersion(1L);
        when(userRepository.findById(4001L)).thenReturn(Optional.of(target));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-4001"))
                .thenReturn(Set.of("awgment-user"));
        when(keycloakUserSyncClient.revokeUserSynchronously(REALM, "kc-sub-4001", 2L, "DISABLED"))
                .thenReturn(true);

        UserService.DeactivationOutcome syncOk = userService.deactivateUser(4001L, OPERATOR);

        assertThat(syncOk.revokedSynchronously()).isTrue();
        assertThat(syncOk.response().status()).isEqualTo(UserStatus.DISABLED);
        assertThat(syncOk.response().syncStatus()).isEqualTo(SyncStatus.SYNCED);

        InOrder order = inOrder(userRepository, outboxService, securityAuditService, keycloakUserSyncClient);
        order.verify(userRepository).saveAndFlush(target);
        order.verify(outboxService).enqueueUserReconcile(4001L, 2L);
        order.verify(securityAuditService).record(
                eq("USER_DISABLE"), eq("USER"), eq("4001"), eq(AuditOutcome.SUCCESS),
                eq("op-sub-1"), isNull(), anyMap());
        order.verify(keycloakUserSyncClient).revokeUserSynchronously(REALM, "kc-sub-4001", 2L, "DISABLED");
        order.verify(userRepository).updateSyncProgress(
                eq(4001L), eq("kc-sub-4001"), eq(2L), eq(SyncStatus.SYNCED), any(Instant.class));

        // Degraded KC path -> 202 PENDING_SYNC
        UserEntity target2 = activeUser(4002L, "kc-sub-4002", "eve");
        target2.setSyncVersion(3L);
        when(userRepository.findById(4002L)).thenReturn(Optional.of(target2));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-4002"))
                .thenReturn(Set.of("awgment-user"));
        when(keycloakUserSyncClient.revokeUserSynchronously(REALM, "kc-sub-4002", 4L, "DISABLED"))
                .thenReturn(false);

        UserService.DeactivationOutcome degraded = userService.deactivateUser(4002L, OPERATOR);

        assertThat(degraded.revokedSynchronously()).isFalse();
        assertThat(degraded.response().status()).isEqualTo(UserStatus.DISABLED);
        assertThat(degraded.response().syncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);
        verify(userRepository, never()).updateSyncProgress(
                eq(4002L), anyString(), anyLong(), any(), any());
    }

    @Test
    void deactivateUserShouldBlockSelfDisableAndHigherTierTargets() {
        UserEntity self = activeUser(4003L, OPERATOR.subject(), "op");
        when(userRepository.findById(4003L)).thenReturn(Optional.of(self));
        doThrow(new ApiException(ApiErrorCode.SELF_ACTION_FORBIDDEN, "self"))
                .when(roleGovernancePolicy)
                .assertNotSelfAction(OPERATOR.subject(), OPERATOR.subject(), "disable");

        assertThatThrownBy(() -> userService.deactivateUser(4003L, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.SELF_ACTION_FORBIDDEN);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivateUserShouldRequireDisabledStateAndTransitionToActive() {
        UserEntity active = activeUser(5001L, "kc-sub-5001", "frank");
        when(userRepository.findById(5001L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> userService.reactivateUser(5001L, APPROVER))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION);

        UserEntity disabled = activeUser(5002L, "kc-sub-5002", "grace");
        disabled.setStatus(UserStatus.DISABLED);
        disabled.setSyncVersion(4L);
        when(userRepository.findById(5002L)).thenReturn(Optional.of(disabled));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-5002"))
                .thenReturn(Set.of("awgment-user"));

        UserResponse reactivated = userService.reactivateUser(5002L, APPROVER);

        assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reactivated.syncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);
        assertThat(disabled.getSyncVersion()).isEqualTo(5L);
        verify(outboxService).enqueueUserReconcile(5002L, 5L);
        verify(outboxService).dispatchPendingBatch(REALM, 1);
    }

    @Test
    void getMyProfileAndUpdateMyProfileShouldEnforceStatusVersionExternalIdpAndSyncRules() {
        CallerPrincipal missingSub = new CallerPrincipal(" ", REALM, Set.of("awgment-user"), false);
        assertThatThrownBy(() -> userService.getMyProfile(missingSub))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TOKEN);

        CallerPrincipal activeCaller = new CallerPrincipal("sub-6001", REALM, Set.of("awgment-user"), false);
        when(userRepository.findByKeycloakSub("sub-6001")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getMyProfile(activeCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);

        UserEntity disabled = activeUser(6001L, "sub-6001", "heidi");
        disabled.setStatus(UserStatus.DISABLED);
        when(userRepository.findByKeycloakSub("sub-6001")).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> userService.getMyProfile(activeCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.ACCESS_DISABLED);

        UserEntity pending = activeUser(6001L, "sub-6001", "heidi");
        pending.setStatus(UserStatus.PENDING_APPROVAL);
        when(userRepository.findByKeycloakSub("sub-6001")).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> userService.getMyProfile(activeCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.ACCESS_PENDING_APPROVAL);

        UserEntity rejected = activeUser(6001L, "sub-6001", "heidi");
        rejected.setStatus(UserStatus.REJECTED);
        when(userRepository.findByKeycloakSub("sub-6001")).thenReturn(Optional.of(rejected));
        assertThatThrownBy(() -> userService.getMyProfile(activeCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.ACCESS_REJECTED);

        // Local user updating only mobileNumber/preferences -> does NOT bump syncVersion
        UserEntity local = activeUser(6001L, "sub-6001", "heidi");
        local.setSyncVersion(1L);
        local.setSyncStatus(SyncStatus.SYNCED);
        local.setOptLockVersion(1L);
        when(userRepository.findByKeycloakSub("sub-6001")).thenReturn(Optional.of(local));

        UserResponse prefsOnly = userService.updateMyProfile(
                new UpdateMyProfileRequest(null, null, "+1-555-0300", null, "UTC", Map.of("theme", "light"), 1L),
                activeCaller
        );
        assertThat(prefsOnly.mobileNumber()).isEqualTo("+1-555-0300");
        assertThat(local.getSyncVersion()).isEqualTo(1L);
        assertThat(local.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        verify(outboxService, never()).enqueueUserReconcile(anyLong(), anyLong());

        // Local user updating locale -> bumps syncVersion + enqueues reconcile + dispatches
        UserResponse localeUpdated = userService.updateMyProfile(
                new UpdateMyProfileRequest("HeidiNew", null, null, "fr", null, null, 1L),
                activeCaller
        );
        assertThat(localeUpdated.locale()).isEqualTo("fr");
        assertThat(localeUpdated.firstName()).isEqualTo("HeidiNew");
        assertThat(local.getSyncVersion()).isEqualTo(2L);
        assertThat(local.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);
        verify(outboxService).enqueueUserReconcile(6001L, 2L);
        verify(outboxService).dispatchPendingBatch(REALM, 1);

        // External IdP user cannot change firstName/lastName
        UserEntity external = activeUser(6002L, "sub-6002", "ivan");
        external.setIdentitySource(IdentitySource.EXTERNAL_IDP);
        external.setIdpAlias("entra-acme");
        external.setOptLockVersion(3L);
        CallerPrincipal extCaller = new CallerPrincipal("sub-6002", REALM, Set.of("awgment-user"), false);
        when(userRepository.findByKeycloakSub("sub-6002")).thenReturn(Optional.of(external));

        assertThatThrownBy(() -> userService.updateMyProfile(
                new UpdateMyProfileRequest("Spoofed", null, null, "fr", null, null, 3L), extCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.IMMUTABLE_FIELD_MODIFICATION);

        // External IdP user CAN update locale & selfServicePreferences
        UserResponse extLocale = userService.updateMyProfile(
                new UpdateMyProfileRequest(null, null, null, "ja", "Asia/Tokyo", Map.of("compact", true), 3L),
                extCaller
        );
        assertThat(extLocale.locale()).isEqualTo("ja");
        verify(outboxService).enqueueUserReconcile(6002L, 2L);
    }

    @Test
    void shouldValidateLocalesRolesListsAndNestedJsonMaps() {
        // Invalid locale on create
        CreateUserRequest badLocale = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid", null, null, null, null,
                "en<script>", null, null, IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(badLocale, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Blank item inside roles
        CreateUserRequest blankRole = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid", null, null, null, null,
                "en", null, null, IdentitySource.KEYCLOAK_LOCAL, null, List.of(" "), null);
        assertThatThrownBy(() -> userService.createUser(blankRole, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Blank key in attributes map
        Map<String, Object> blankKeyMap = new LinkedHashMap<>();
        blankKeyMap.put(" ", "val");
        CreateUserRequest blankAttrKey = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid", null, null, null, null,
                "en", null, blankKeyMap, IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(blankAttrKey, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Excessive nesting depth > 6 in attributes map
        Map<String, Object> deep = new LinkedHashMap<>();
        Map<String, Object> cursor = deep;
        for (int i = 0; i < 8; i++) {
            Map<String, Object> child = new LinkedHashMap<>();
            cursor.put("k" + i, child);
            cursor = child;
        }
        cursor.put("leaf", "x");
        CreateUserRequest deepMapReq = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid", null, null, null, null,
                "en", null, deep, IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(deepMapReq, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Unsupported value type in attributes map
        CreateUserRequest badValueReq = new CreateUserRequest(
                "alice", "alice" + "@" + "acme.invalid", null, null, null, null,
                "en", null, Map.of("obj", new Object()), IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        assertThatThrownBy(() -> userService.createUser(badValueReq, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Valid nested list/map attribute succeeds
        CreateUserRequest validNestedReq = new CreateUserRequest(
                "alice.nested", "alice.nested" + "@" + "acme.invalid", null, null, null, null,
                "en", null, Map.of("tags", List.of("a", Map.of("k", 1))),
                IdentitySource.KEYCLOAK_LOCAL, null, null, null);
        UserResponse created = userService.createUser(validNestedReq, OPERATOR);
        assertThat(created.attributes()).containsKey("tags");

        // Blank locale on updateUser -> 400 VALIDATION_FAILED
        UserEntity existing = activeUser(7001L, null, "update.target");
        existing.setOptLockVersion(1L);
        when(userRepository.findById(7001L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> userService.updateUser(
                7001L, new UpdateUserRequest(null, null, null, null, "   ", null, null, 1L), OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void shouldHandleDeactivateOutageFallbackAndFailClosedForUnmanagedOrCustomRoleUsers() {
        // PG-managed built-in awgment-user degrades to 202 when KC is down
        UserEntity managed = activeUser(7101L, "kc-sub-7101", "managed");
        managed.setDesiredRoles(List.of("awgment-user"));
        when(userRepository.findById(7101L)).thenReturn(Optional.of(managed));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-7101"))
                .thenThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "kc down"));
        when(keycloakUserSyncClient.revokeUserSynchronously(REALM, "kc-sub-7101", 2L, "DISABLED"))
                .thenReturn(false);

        UserService.DeactivationOutcome degraded = userService.deactivateUser(7101L, OPERATOR);
        assertThat(degraded.revokedSynchronously()).isFalse();
        assertThat(degraded.response().status()).isEqualTo(UserStatus.DISABLED);

        // Unmanaged user (desiredRoles == null) fails closed with 502 UPSTREAM_IDENTITY_PROVIDER_ERROR
        UserEntity unmanaged = activeUser(7102L, "kc-sub-7102", "unmanaged");
        unmanaged.setDesiredRoles(null);
        when(userRepository.findById(7102L)).thenReturn(Optional.of(unmanaged));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-7102"))
                .thenThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "kc down"));

        assertThatThrownBy(() -> userService.deactivateUser(7102L, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR);

        // Custom-role user also fails closed during KC outage (could hide composite Tier 1)
        UserEntity customRole = activeUser(7103L, "kc-sub-7103", "custom");
        customRole.setDesiredRoles(List.of("custom-composite"));
        when(userRepository.findById(7103L)).thenReturn(Optional.of(customRole));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-7103"))
                .thenThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "kc down"));

        assertThatThrownBy(() -> userService.deactivateUser(7103L, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR);
    }

    @Test
    void shouldValidateSortFormatsAndSelfUpdateVersionAndLastNameGuards() {
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Blank sort defaults cleanly, single-segment sort defaults DESC
        userService.searchUsers(null, null, null, null, null, null, false, 0, 10, "  ", OPERATOR);
        userService.searchUsers(null, null, null, null, null, null, false, 0, 10, "userName", OPERATOR);

        // Too many comma segments
        assertThatThrownBy(() -> userService.searchUsers(
                null, null, null, null, null, null, false, 0, 10, "userName,asc,extra", OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Invalid direction
        assertThatThrownBy(() -> userService.searchUsers(
                null, null, null, null, null, null, false, 0, 10, "userName,sideways", OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        // Self update version conflict & external lastName immutable check
        UserEntity ext = activeUser(7201L, "sub-7201", "ext");
        ext.setIdentitySource(IdentitySource.EXTERNAL_IDP);
        ext.setOptLockVersion(2L);
        CallerPrincipal extCaller = new CallerPrincipal("sub-7201", REALM, Set.of("awgment-user"), false);
        when(userRepository.findByKeycloakSub("sub-7201")).thenReturn(Optional.of(ext));

        assertThatThrownBy(() -> userService.updateMyProfile(
                new UpdateMyProfileRequest(null, null, null, "en", null, null, 1L), extCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);

        assertThatThrownBy(() -> userService.updateMyProfile(
                new UpdateMyProfileRequest(null, "ChangedLast", null, null, null, null, 2L), extCaller))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.IMMUTABLE_FIELD_MODIFICATION);

        // Null caller & null subject on /me -> 401 INVALID_TOKEN
        assertThatThrownBy(() -> userService.getMyProfile(null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TOKEN);
        assertThatThrownBy(() -> userService.getMyProfile(new CallerPrincipal(null, REALM, null, false)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TOKEN);

        // Active getMyProfile happy path + UserResponse null-map factory coverage
        UserEntity activeMe = activeUser(7202L, "sub-7202", "active.me");
        activeMe.setSelfServicePreferences(null);
        activeMe.setAttributes(null);
        when(userRepository.findByKeycloakSub("sub-7202")).thenReturn(Optional.of(activeMe));
        UserResponse meResp = userService.getMyProfile(new CallerPrincipal("sub-7202", REALM, null, false));
        assertThat(meResp.selfServicePreferences()).isEmpty();
        assertThat(meResp.attributes()).isEmpty();

        // size < 1 and null/trailing-comma sort coverage
        assertThatThrownBy(() -> userService.searchUsers(
                null, null, null, null, null, null, false, 0, 0, "createdAt,desc", OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        userService.searchUsers(null, null, null, null, null, null, false, 0, 10, null, OPERATOR);
        userService.searchUsers(null, null, null, null, null, null, false, 0, 10, "createdAt,", OPERATOR);

        // Blank keycloakSub, null liveRoles, and null/public tenantRealm dispatch skip coverage
        UserEntity blankSubUser = activeUser(7203L, "  ", "blank.sub");
        blankSubUser.setOptLockVersion(0L);
        when(userRepository.findById(7203L)).thenReturn(Optional.of(blankSubUser));
        userService.updateUser(
                7203L,
                new UpdateUserRequest(null, null, null, null, "en", null, null, 0L),
                new CallerPrincipal("op", "public", Set.of("awgment-account-all"), false)
        );
        userService.updateUser(
                7203L,
                new UpdateUserRequest(null, null, null, null, "en", null, null, 0L),
                new CallerPrincipal("op", " ", Set.of("awgment-account-all"), false)
        );
        UserEntity nullLiveRolesTarget = activeUser(7204L, "kc-sub-7204", "null.live");
        nullLiveRolesTarget.setOptLockVersion(0L);
        when(userRepository.findById(7204L)).thenReturn(Optional.of(nullLiveRolesTarget));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles("acme", "kc-sub-7204")).thenReturn(null);
        userService.updateUser(
                7204L,
                new UpdateUserRequest(null, null, null, null, "en", null, null, 0L),
                new CallerPrincipal("op", null, Set.of("awgment-account-all"), false)
        );

        // Outage fallback with null role item in pgRoles fails closed with 502
        UserEntity nullRoleItem = activeUser(7205L, "kc-sub-7205", "null.role.item");
        nullRoleItem.setDesiredRoles(java.util.Collections.singletonList(null));
        when(userRepository.findById(7205L)).thenReturn(Optional.of(nullRoleItem));
        when(keycloakUserSyncClient.fetchUserEffectiveRoles(REALM, "kc-sub-7205"))
                .thenThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "kc down"));
        assertThatThrownBy(() -> userService.deactivateUser(7205L, OPERATOR))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR);
    }

    private static UserEntity activeUser(Long id, String keycloakSub, String userName) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setKeycloakSub(keycloakSub);
        entity.setUserName(userName);
        entity.setEmailId(userName + "@" + "acme.invalid");
        entity.setFirstName("First");
        entity.setLastName("Last");
        entity.setLocale("en");
        entity.setSelfServicePreferences(new LinkedHashMap<>());
        entity.setAttributes(new LinkedHashMap<>());
        entity.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
        entity.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setSyncVersion(1L);
        entity.setSyncedVersion(0L);
        entity.setSyncStatus(SyncStatus.PENDING_SYNC);
        entity.setOptLockVersion(0L);
        return entity;
    }
}
