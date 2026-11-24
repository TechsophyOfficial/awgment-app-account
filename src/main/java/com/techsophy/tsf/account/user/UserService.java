package com.techsophy.tsf.account.user;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.governance.RoleCompositeExpander;
import com.techsophy.tsf.account.governance.RoleGovernancePolicy;
import com.techsophy.tsf.account.keycloak.KeycloakUserSyncClient;
import com.techsophy.tsf.account.outbox.OutboxService;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.user.dto.CreateUserRequest;
import com.techsophy.tsf.account.user.dto.UpdateMyProfileRequest;
import com.techsophy.tsf.account.user.dto.UpdateUserRequest;
import com.techsophy.tsf.account.user.dto.UserDeactivationResponse;
import com.techsophy.tsf.account.user.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orchestrates tenant user profile lifecycle operations (UC-USER-01..07),
 * keeping all Keycloak network I/O outside PostgreSQL transactions.
 */
@Slf4j
@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "userName",
            "emailId",
            "firstName",
            "lastName",
            "department",
            "status",
            "createdAt",
            "updatedAt"
    );
    private static final Set<String> BUILTIN_NON_COMPOSITE_ROLES = Set.of(
            "awgment-platform-admin",
            "awgment-account-admin",
            "awgment-account-all",
            "awgment-account-create-or-update",
            "awgment-account-read",
            "awgment-account-delete",
            "awgment-acl-create-or-update",
            "awgment-acl-read",
            "awgment-acl-evaluate-context",
            "awgment-acl-evaluate-delegated",
            "awgment-system-service",
            "awgment-user"
    );

    private final UserRepository userRepository;
    private final UserPersistenceService persistenceService;
    private final RoleGovernancePolicy roleGovernancePolicy;
    private final OutboxService outboxService;
    private final KeycloakUserSyncClient keycloakUserSyncClient;

    public UserService(
            UserRepository userRepository,
            UserPersistenceService persistenceService,
            RoleGovernancePolicy roleGovernancePolicy,
            OutboxService outboxService,
            KeycloakUserSyncClient keycloakUserSyncClient) {
        this.userRepository = userRepository;
        this.persistenceService = persistenceService;
        this.roleGovernancePolicy = roleGovernancePolicy;
        this.outboxService = outboxService;
        this.keycloakUserSyncClient = keycloakUserSyncClient;
    }

    public UserResponse createUser(CreateUserRequest request, CallerPrincipal caller) {
        UserResponse created = persistenceService.createUserTx(request, caller);
        dispatchOutboxSafely(caller.tenantRealm());
        return created;
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request, CallerPrincipal caller) {
        UserEntity snapshot = loadUserOrThrow(id);
        Set<String> targetRoles = collectTargetRolesWithLiveKeycloak(caller.tenantRealm(), snapshot);
        roleGovernancePolicy.assertCanManageTargetUser(caller.tenantRealm(), caller.directRoles(), targetRoles);

        UserResponse updated = persistenceService.updateUserTx(id, request, caller);
        dispatchOutboxSafely(caller.tenantRealm());
        return updated;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, boolean onlyMandatoryFields) {
        UserEntity user = loadUserOrThrow(id);
        return onlyMandatoryFields ? UserResponse.mandatoryOnly(user) : UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(
            String q,
            UserStatus status,
            IdentitySource identitySource,
            String idpAlias,
            String department,
            String loginId,
            boolean onlyMandatoryFields,
            int page,
            int size,
            String sort,
            CallerPrincipal caller) {
        if (page < 0) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Page index must be >= 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + "."
            );
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Page<UserEntity> entityPage = userRepository.findAll(
                UserSpecifications.buildSpecification(
                        q,
                        status,
                        identitySource,
                        idpAlias,
                        department,
                        loginId,
                        caller.verifiedServiceAccount()
                ),
                pageable
        );
        return PageResponse.from(entityPage.map(entity ->
                onlyMandatoryFields ? UserResponse.mandatoryOnly(entity) : UserResponse.from(entity)));
    }

    public DeactivationOutcome deactivateUser(Long id, CallerPrincipal caller) {
        UserEntity snapshot = loadUserOrThrow(id);
        roleGovernancePolicy.assertNotSelfAction(caller.subject(), snapshot.getKeycloakSub(), "disable");
        assertCanManageForDeactivation(caller, snapshot);

        // Phase A: Commit PostgreSQL DISABLED status + coalesced RECONCILE_USER + audit log
        UserPersistenceService.DeactivatePhaseAResult phaseA =
                persistenceService.deactivateUserPhaseATx(id, caller);

        // Phase B: Synchronous-first Keycloak disable + logout outside PostgreSQL TX
        boolean revoked = keycloakUserSyncClient.revokeUserSynchronously(
                caller.tenantRealm(),
                phaseA.keycloakSub(),
                phaseA.syncVersion(),
                UserStatus.DISABLED.name()
        );
        if (revoked) {
            persistenceService.markRevocationSyncedTx(
                    phaseA.userId(),
                    phaseA.keycloakSub(),
                    phaseA.syncVersion()
            );
            return new DeactivationOutcome(
                    true,
                    new UserDeactivationResponse(UserStatus.DISABLED, SyncStatus.SYNCED)
            );
        }
        return new DeactivationOutcome(
                false,
                new UserDeactivationResponse(UserStatus.DISABLED, SyncStatus.PENDING_SYNC)
        );
    }

    public UserResponse reactivateUser(Long id, CallerPrincipal caller) {
        UserEntity snapshot = loadUserOrThrow(id);
        roleGovernancePolicy.assertNotSelfAction(caller.subject(), snapshot.getKeycloakSub(), "reactivate");
        Set<String> targetRoles = collectTargetRolesWithLiveKeycloak(caller.tenantRealm(), snapshot);
        roleGovernancePolicy.assertCanManageTargetUser(caller.tenantRealm(), caller.directRoles(), targetRoles);

        UserResponse reactivated = persistenceService.reactivateUserTx(id, caller);
        dispatchOutboxSafely(caller.tenantRealm());
        return reactivated;
    }

    @Transactional(readOnly = true)
    public UserResponse getMyProfile(CallerPrincipal caller) {
        String keycloakSub = requireAuthenticatedSubject(caller);
        UserEntity user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Authenticated user profile was not found."
                ));
        UserPersistenceService.assertActiveStatus(user);
        return UserResponse.from(user);
    }

    public UserResponse updateMyProfile(UpdateMyProfileRequest request, CallerPrincipal caller) {
        String keycloakSub = requireAuthenticatedSubject(caller);
        UserPersistenceService.SelfUpdateResult result = persistenceService.updateMyProfileTx(request, keycloakSub);
        if (result.dispatchRequired()) {
            dispatchOutboxSafely(caller.tenantRealm());
        }
        return result.response();
    }

    private void assertCanManageForDeactivation(CallerPrincipal caller, UserEntity target) {
        try {
            Set<String> targetRoles = collectTargetRolesWithLiveKeycloak(caller.tenantRealm(), target);
            roleGovernancePolicy.assertCanManageTargetUser(caller.tenantRealm(), caller.directRoles(), targetRoles);
        } catch (ApiException ex) {
            if (ex.errorCode() != ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR) {
                throw ex;
            }
            // During Keycloak outages, preserve Scenario 2 degraded 202 revocation ONLY when
            // PostgreSQL has authoritative non-composite roles (avoiding privilege bypass on unmanaged users).
            List<String> pgRoles = target.getDesiredRoles();
            if (pgRoles == null || !allBuiltinNonComposite(pgRoles)) {
                throw ex;
            }
            new RoleGovernancePolicy(RoleCompositeExpander.IDENTITY)
                    .assertCanManageTargetUser(caller.tenantRealm(), caller.directRoles(), pgRoles);
        }
    }

    private static boolean allBuiltinNonComposite(List<String> roles) {
        for (String role : roles) {
            if (role == null || !BUILTIN_NON_COMPOSITE_ROLES.contains(role.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private Set<String> collectTargetRolesWithLiveKeycloak(String tenantRealm, UserEntity target) {
        Set<String> merged = new LinkedHashSet<>();
        if (target.getDesiredRoles() != null) {
            merged.addAll(target.getDesiredRoles());
        }
        if (target.getKeycloakSub() != null && !target.getKeycloakSub().isBlank()) {
            Set<String> liveRoles = keycloakUserSyncClient.fetchUserEffectiveRoles(
                    tenantRealm, target.getKeycloakSub());
            if (liveRoles != null) {
                merged.addAll(liveRoles);
            }
        }
        return merged;
    }

    private UserEntity loadUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "User '" + id + "' was not found."
                ));
    }

    private static String requireAuthenticatedSubject(CallerPrincipal caller) {
        if (caller == null || caller.subject() == null || caller.subject().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Authenticated subject claim is missing.");
        }
        return caller.subject().trim();
    }

    private void dispatchOutboxSafely(String tenantRealm) {
        if (tenantRealm == null || tenantRealm.isBlank() || "public".equals(tenantRealm)) {
            return;
        }
        try {
            outboxService.dispatchPendingBatch(tenantRealm, 1);
        } catch (RuntimeException ex) {
            log.warn("Post-commit outbox dispatch deferred for tenant '{}': {}",
                    ProblemDetailFactory.sanitizeForLog(tenantRealm),
                    ProblemDetailFactory.sanitizeForLog(ex.getMessage()));
        }
    }

    private static Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sortParam.split(",", -1);
        if (parts.length > 2) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Invalid sort parameter format.");
        }
        String property = parts[0].trim();
        if (!ALLOWED_SORT_PROPERTIES.contains(property)) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "Sort property '" + ProblemDetailFactory.sanitizeForLog(property) + "' is not allowed."
            );
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Sort direction must be 'asc' or 'desc'.");
            }
        }
        return Sort.by(direction, property);
    }

    public record DeactivationOutcome(boolean revokedSynchronously, UserDeactivationResponse response) {
    }
}
