package com.techsophy.tsf.account.user;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.governance.RoleGovernancePolicy;
import com.techsophy.tsf.account.idp.TrustedIdpEntity;
import com.techsophy.tsf.account.idp.TrustedIdpRepository;
import com.techsophy.tsf.account.outbox.OutboxService;
import com.techsophy.tsf.account.user.dto.CreateUserRequest;
import com.techsophy.tsf.account.user.dto.UpdateMyProfileRequest;
import com.techsophy.tsf.account.user.dto.UpdateUserRequest;
import com.techsophy.tsf.account.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Single-transaction PostgreSQL persistence boundary for user lifecycle mutations,
 * coalesced outbox enqueuing, and audit log emission (zero network I/O inside TX).
 */
@Service
public class UserPersistenceService {

    private static final int ACTION_EMAIL_LIFESPAN_SECONDS = 24 * 60 * 60;
    private static final int MAX_MAP_DEPTH = 6;
    private static final Pattern BCP47_LOCALE_PATTERN = Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");

    private final UserRepository userRepository;
    private final TrustedIdpRepository trustedIdpRepository;
    private final RoleGovernancePolicy roleGovernancePolicy;
    private final OutboxService outboxService;
    private final SecurityAuditService securityAuditService;
    private final IdGeneratorImpl idGenerator;

    public UserPersistenceService(
            UserRepository userRepository,
            TrustedIdpRepository trustedIdpRepository,
            RoleGovernancePolicy roleGovernancePolicy,
            OutboxService outboxService,
            SecurityAuditService securityAuditService,
            IdGeneratorImpl idGenerator) {
        this.userRepository = userRepository;
        this.trustedIdpRepository = trustedIdpRepository;
        this.roleGovernancePolicy = roleGovernancePolicy;
        this.outboxService = outboxService;
        this.securityAuditService = securityAuditService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public UserResponse createUserTx(CreateUserRequest request, CallerPrincipal caller) {
        String normalizedUserName = request.userName().trim().toLowerCase(Locale.ROOT);
        if (normalizedUserName.startsWith("service-account")) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "userName must not start with the reserved 'service-account' prefix."
            );
        }
        String normalizedEmail = request.emailId().trim().toLowerCase(Locale.ROOT);
        String normalizedLocale = normalizeLocaleForCreate(request.locale());
        List<String> normalizedRoles = normalizeStringList(request.roles(), "roles");
        List<String> normalizedGroups = normalizeStringList(request.groups(), "groups");
        Map<String, Object> sanitizedAttributes = sanitizeJsonMap(request.attributes(), "attributes");

        IdentitySource identitySource = request.identitySource() != null
                ? request.identitySource()
                : IdentitySource.KEYCLOAK_LOCAL;
        String resolvedIdpAlias = validateIdentitySourceAndIdp(identitySource, request.idpAlias());

        if (userRepository.existsActiveOrDisabledByEmail(normalizedEmail)) {
            throw new ApiException(
                    ApiErrorCode.EMAIL_ALREADY_CLAIMED,
                    "An active or disabled user already claims this email address."
            );
        }
        if (userRepository.existsActiveOrDisabledByUserName(normalizedUserName)) {
            throw new ApiException(
                    ApiErrorCode.DUPLICATE_RESOURCE,
                    "A user with username '" + ProblemDetailFactory.sanitizeForLog(normalizedUserName) + "' already exists."
            );
        }

        roleGovernancePolicy.assertCanAssignRoles(
                caller.tenantRealm(),
                caller.directRoles(),
                List.of(),
                normalizedRoles != null ? normalizedRoles : List.of()
        );

        UserEntity entity = new UserEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setUserName(normalizedUserName);
        entity.setEmailId(normalizedEmail);
        entity.setFirstName(trimToNull(request.firstName()));
        entity.setLastName(trimToNull(request.lastName()));
        entity.setMobileNumber(trimToNull(request.mobileNumber()));
        entity.setDepartment(trimToNull(request.department()));
        entity.setLocale(normalizedLocale);
        entity.setTimezone(trimToNull(request.timezone()));
        entity.setSelfServicePreferences(new LinkedHashMap<>());
        entity.setAttributes(sanitizedAttributes);
        entity.setIdentitySource(identitySource);
        entity.setIdpAlias(resolvedIdpAlias);
        entity.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setDesiredRoles(normalizedRoles);
        entity.setDesiredGroups(normalizedGroups);
        entity.setSyncVersion(1L);
        entity.setSyncedVersion(0L);
        entity.setSyncStatus(SyncStatus.PENDING_SYNC);
        entity.setCreatedBy(caller.subject());
        entity.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(entity);

        outboxService.enqueueUserReconcile(saved.getId(), saved.getSyncVersion());
        if (identitySource == IdentitySource.KEYCLOAK_LOCAL) {
            outboxService.enqueueActionEmail(
                    saved.getId(),
                    List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"),
                    ACTION_EMAIL_LIFESPAN_SECONDS
            );
        }

        Map<String, Object> auditMeta = new LinkedHashMap<>();
        auditMeta.put("identitySource", identitySource.name());
        auditMeta.put("provisioningMode", ProvisioningMode.DIRECT_OPERATOR.name());
        if (resolvedIdpAlias != null) {
            auditMeta.put("idpAlias", resolvedIdpAlias);
        }
        securityAuditService.record(
                "USER_CREATE",
                "USER",
                String.valueOf(saved.getId()),
                AuditOutcome.SUCCESS,
                caller.subject(),
                null,
                auditMeta
        );

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateUserTx(Long id, UpdateUserRequest request, CallerPrincipal caller) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "User '" + id + "' was not found."
                ));
        if (!Objects.equals(request.version(), user.getOptLockVersion())) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }

        if (request.firstName() != null) {
            user.setFirstName(trimToNull(request.firstName()));
        }
        if (request.lastName() != null) {
            user.setLastName(trimToNull(request.lastName()));
        }
        if (request.mobileNumber() != null) {
            user.setMobileNumber(trimToNull(request.mobileNumber()));
        }
        if (request.department() != null) {
            user.setDepartment(trimToNull(request.department()));
        }
        if (request.locale() != null) {
            user.setLocale(normalizeLocaleForUpdate(request.locale()));
        }
        if (request.timezone() != null) {
            user.setTimezone(trimToNull(request.timezone()));
        }
        if (request.attributes() != null) {
            user.setAttributes(sanitizeJsonMap(request.attributes(), "attributes"));
        }

        long nextSyncVersion = nextSyncVersion(user);
        user.setSyncVersion(nextSyncVersion);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(user);
        outboxService.enqueueUserReconcile(saved.getId(), saved.getSyncVersion());
        securityAuditService.record(
                "USER_UPDATE",
                "USER",
                String.valueOf(saved.getId()),
                AuditOutcome.SUCCESS,
                caller.subject(),
                null,
                Map.of("syncVersion", saved.getSyncVersion())
        );

        return UserResponse.from(saved);
    }

    @Transactional
    public DeactivatePhaseAResult deactivateUserPhaseATx(Long id, CallerPrincipal caller) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "User '" + id + "' was not found."
                ));
        long nextSyncVersion = nextSyncVersion(user);
        user.setStatus(UserStatus.DISABLED);
        user.setSyncVersion(nextSyncVersion);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(user);
        outboxService.enqueueUserReconcile(saved.getId(), saved.getSyncVersion());
        securityAuditService.record(
                "USER_DISABLE",
                "USER",
                String.valueOf(saved.getId()),
                AuditOutcome.SUCCESS,
                caller.subject(),
                null,
                Map.of("syncMode", "SYNC_FIRST", "syncVersion", saved.getSyncVersion())
        );
        return new DeactivatePhaseAResult(saved.getId(), saved.getKeycloakSub(), saved.getSyncVersion());
    }

    @Transactional
    public void markRevocationSyncedTx(Long userId, String keycloakSub, long syncVersion) {
        userRepository.updateSyncProgress(userId, keycloakSub, syncVersion, SyncStatus.SYNCED, Instant.now());
    }

    @Transactional
    public UserResponse reactivateUserTx(Long id, CallerPrincipal caller) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "User '" + id + "' was not found."
                ));
        if (user.getStatus() != UserStatus.DISABLED) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "User '" + id + "' cannot be reactivated from status " + user.getStatus() + "."
            );
        }

        long nextSyncVersion = nextSyncVersion(user);
        user.setStatus(UserStatus.ACTIVE);
        user.setSyncVersion(nextSyncVersion);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(user);
        outboxService.enqueueUserReconcile(saved.getId(), saved.getSyncVersion());
        securityAuditService.record(
                "USER_REACTIVATE",
                "USER",
                String.valueOf(saved.getId()),
                AuditOutcome.SUCCESS,
                caller.subject(),
                null,
                Map.of("syncVersion", saved.getSyncVersion())
        );
        return UserResponse.from(saved);
    }

    @Transactional
    public SelfUpdateResult updateMyProfileTx(UpdateMyProfileRequest request, String keycloakSub) {
        UserEntity user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Authenticated user profile was not found."
                ));
        assertActiveStatus(user);

        if (!Objects.equals(request.version(), user.getOptLockVersion())) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }

        boolean firstNameProvided = request.firstName() != null;
        boolean lastNameProvided = request.lastName() != null;
        String nextFirstName = firstNameProvided ? trimToNull(request.firstName()) : user.getFirstName();
        String nextLastName = lastNameProvided ? trimToNull(request.lastName()) : user.getLastName();

        boolean firstNameChanged = firstNameProvided && !Objects.equals(nextFirstName, user.getFirstName());
        boolean lastNameChanged = lastNameProvided && !Objects.equals(nextLastName, user.getLastName());

        if (user.getIdentitySource() == IdentitySource.EXTERNAL_IDP && (firstNameChanged || lastNameChanged)) {
            throw new ApiException(
                    ApiErrorCode.IMMUTABLE_FIELD_MODIFICATION,
                    "firstName and lastName are managed by the external identity provider and cannot be modified."
            );
        }

        if (user.getIdentitySource() == IdentitySource.KEYCLOAK_LOCAL) {
            if (firstNameProvided) {
                user.setFirstName(nextFirstName);
            }
            if (lastNameProvided) {
                user.setLastName(nextLastName);
            }
        }

        if (request.mobileNumber() != null) {
            user.setMobileNumber(trimToNull(request.mobileNumber()));
        }
        if (request.timezone() != null) {
            user.setTimezone(trimToNull(request.timezone()));
        }

        boolean localeChanged = false;
        if (request.locale() != null) {
            String nextLocale = normalizeLocaleForUpdate(request.locale());
            localeChanged = !Objects.equals(nextLocale, user.getLocale());
            user.setLocale(nextLocale);
        }

        if (request.selfServicePreferences() != null) {
            user.setSelfServicePreferences(
                    sanitizeJsonMap(request.selfServicePreferences(), "selfServicePreferences"));
        }

        boolean keycloakSyncedChanged = localeChanged || firstNameChanged || lastNameChanged;
        if (keycloakSyncedChanged) {
            user.setSyncVersion(nextSyncVersion(user));
            user.setSyncStatus(SyncStatus.PENDING_SYNC);
        }
        user.setUpdatedBy(keycloakSub);

        UserEntity saved = userRepository.saveAndFlush(user);
        if (keycloakSyncedChanged) {
            outboxService.enqueueUserReconcile(saved.getId(), saved.getSyncVersion());
        }
        securityAuditService.record(
                "USER_SELF_UPDATE",
                "USER",
                String.valueOf(saved.getId()),
                AuditOutcome.SUCCESS,
                keycloakSub,
                saved.getId(),
                Map.of("keycloakSynced", keycloakSyncedChanged)
        );
        return new SelfUpdateResult(UserResponse.from(saved), keycloakSyncedChanged);
    }

    static void assertActiveStatus(UserEntity user) {
        UserStatus status = user.getStatus();
        if (status == UserStatus.ACTIVE) {
            return;
        }
        if (status == UserStatus.DISABLED) {
            throw new ApiException(ApiErrorCode.ACCESS_DISABLED, "User account is disabled.");
        }
        if (status == UserStatus.PENDING_APPROVAL) {
            throw new ApiException(ApiErrorCode.ACCESS_PENDING_APPROVAL, "User account is pending administrator approval.");
        }
        throw new ApiException(ApiErrorCode.ACCESS_REJECTED, "User account access has been rejected.");
    }

    private String validateIdentitySourceAndIdp(IdentitySource source, String rawIdpAlias) {
        String idpAlias = trimToNull(rawIdpAlias);
        if (source == IdentitySource.EXTERNAL_IDP) {
            if (idpAlias == null) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        "idpAlias is required when identitySource is EXTERNAL_IDP."
                );
            }
            TrustedIdpEntity trustedIdp = trustedIdpRepository.findByIdpAlias(idpAlias)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.VALIDATION_FAILED,
                            "Trusted IdP '" + ProblemDetailFactory.sanitizeForLog(idpAlias) + "' is not registered."
                    ));
            if (!trustedIdp.isEnabled()) {
                throw new ApiException(
                        ApiErrorCode.IDP_TRUST_REVOKED,
                        "Trusted IdP '" + ProblemDetailFactory.sanitizeForLog(idpAlias) + "' is disabled."
                );
            }
            return trustedIdp.getIdpAlias();
        }
        if (idpAlias != null) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "idpAlias must not be specified when identitySource is KEYCLOAK_LOCAL."
            );
        }
        return null;
    }

    private static long nextSyncVersion(UserEntity user) {
        Long current = user.getSyncVersion();
        return (current == null ? 0L : current) + 1L;
    }

    private static String normalizeLocaleForCreate(String rawLocale) {
        if (rawLocale == null || rawLocale.isBlank()) {
            return "en";
        }
        return validateBcp47Locale(rawLocale);
    }

    private static String normalizeLocaleForUpdate(String rawLocale) {
        if (rawLocale.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "locale must not be blank.");
        }
        return validateBcp47Locale(rawLocale);
    }

    private static String validateBcp47Locale(String rawLocale) {
        String trimmed = rawLocale.trim();
        if (trimmed.length() > 35 || !BCP47_LOCALE_PATTERN.matcher(trimmed).matches()) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "locale must be a valid BCP-47 language tag (e.g., en, fr, en-US)."
            );
        }
        return trimmed;
    }

    private static List<String> normalizeStringList(List<String> rawItems, String fieldName) {
        if (rawItems == null) {
            return null;
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String item : rawItems) {
            if (item == null || item.isBlank()) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " must not contain null or blank entries."
                );
            }
            deduplicated.add(item.trim());
        }
        return new ArrayList<>(deduplicated);
    }

    private static Map<String, Object> sanitizeJsonMap(Map<String, Object> rawMap, String fieldName) {
        if (rawMap == null || rawMap.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return copyAndValidateMap(rawMap, fieldName, 0);
    }

    private static Map<String, Object> copyAndValidateMap(Map<?, ?> source, String fieldName, int depth) {
        if (depth > MAX_MAP_DEPTH) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " exceeds maximum allowed nesting depth."
            );
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String rawKey) || rawKey.isBlank()) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " keys must be non-blank strings."
                );
            }
            copy.put(rawKey.trim(), copyAndValidateValue(entry.getValue(), fieldName, depth + 1));
        }
        return copy;
    }

    private static Object copyAndValidateValue(Object value, String fieldName, int depth) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (depth > MAX_MAP_DEPTH) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " exceeds maximum allowed nesting depth."
            );
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return copyAndValidateMap(nestedMap, fieldName, depth);
        }
        if (value instanceof List<?> nestedList) {
            List<Object> copied = new ArrayList<>(nestedList.size());
            for (Object item : nestedList) {
                copied.add(copyAndValidateValue(item, fieldName, depth + 1));
            }
            return copied;
        }
        throw new ApiException(
                ApiErrorCode.VALIDATION_FAILED,
                fieldName + " contains an unsupported value type."
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record DeactivatePhaseAResult(Long userId, String keycloakSub, long syncVersion) {
    }

    public record SelfUpdateResult(UserResponse response, boolean dispatchRequired) {
    }
}
