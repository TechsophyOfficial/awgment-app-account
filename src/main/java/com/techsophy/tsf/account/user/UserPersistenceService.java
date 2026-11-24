package com.techsophy.tsf.account.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.idp.TrustedIdpEntity;
import com.techsophy.tsf.account.idp.TrustedIdpRepository;
import com.techsophy.tsf.account.outbox.CasCompletionResult;
import com.techsophy.tsf.account.outbox.OutboxService;
import com.techsophy.tsf.account.user.dto.CreateUserRequest;
import com.techsophy.tsf.account.user.dto.UpdateMyProfileRequest;
import com.techsophy.tsf.account.user.dto.UpdateUserRequest;
import com.techsophy.tsf.account.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Single-transaction PostgreSQL persistence boundary for user lifecycle mutations,
 * coalesced outbox enqueuing (strict canonical lock order {@code tp_keycloak_outbox -> tp_user}),
 * bounded JSONB/input sanitization, and audit log emission (zero network I/O inside TX).
 */
@Service
public class UserPersistenceService {

    private static final int ACTION_EMAIL_LIFESPAN_SECONDS = 24 * 60 * 60;
    private static final int MAX_MAP_DEPTH = 6;
    private static final int MAX_MAP_ENTRIES = 64;
    private static final int MAX_LIST_ITEMS = 64;
    private static final int MAX_TOTAL_NODES = 128;
    private static final int MAX_JSON_KEY_LENGTH = 128;
    private static final int MAX_JSON_STRING_LENGTH = 2048;
    private static final int MAX_JSON_MAP_BYTES = 16 * 1024;
    private static final int MAX_ROLE_GROUP_LIST_SIZE = 100;
    private static final int MAX_ROLE_GROUP_NAME_LENGTH = 255;
    private static final ObjectMapper JSON_SIZE_MAPPER = new ObjectMapper();
    private static final Pattern BCP47_LOCALE_PATTERN = Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");
    private static final Pattern MOBILE_NUMBER_PATTERN = Pattern.compile("^[+0-9(][0-9\\s().-]{4,31}$");

    private final UserRepository userRepository;
    private final TrustedIdpRepository trustedIdpRepository;
    private final OutboxService outboxService;
    private final SecurityAuditService securityAuditService;
    private final IdGeneratorImpl idGenerator;

    public UserPersistenceService(
            UserRepository userRepository,
            TrustedIdpRepository trustedIdpRepository,
            OutboxService outboxService,
            SecurityAuditService securityAuditService,
            IdGeneratorImpl idGenerator) {
        this.userRepository = userRepository;
        this.trustedIdpRepository = trustedIdpRepository;
        this.outboxService = outboxService;
        this.securityAuditService = securityAuditService;
        this.idGenerator = idGenerator;
    }

    static List<String> validateAndNormalizeCreateInputs(CreateUserRequest request) {
        String normalizedUserName = request.userName().trim().toLowerCase(Locale.ROOT);
        if (normalizedUserName.startsWith("service-account")) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "userName must not start with the reserved 'service-account' prefix."
            );
        }
        normalizeLocaleForCreate(request.locale());
        validateTimezone(request.timezone());
        validateMobileNumber(request.mobileNumber());
        sanitizeJsonMap(request.attributes(), "attributes");
        validateGroupsFailClosed(request.groups());
        return normalizeStringList(request.roles(), "roles");
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
        String normalizedTimezone = validateTimezone(request.timezone());
        String normalizedMobile = validateMobileNumber(request.mobileNumber());
        List<String> normalizedRoles = normalizeStringList(request.roles(), "roles");
        List<String> normalizedGroups = validateGroupsFailClosed(request.groups());
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

        UserEntity entity = new UserEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setUserName(normalizedUserName);
        entity.setEmailId(normalizedEmail);
        entity.setFirstName(trimToNull(request.firstName()));
        entity.setLastName(trimToNull(request.lastName()));
        entity.setMobileNumber(normalizedMobile);
        entity.setDepartment(trimToNull(request.department()));
        entity.setLocale(normalizedLocale);
        entity.setTimezone(normalizedTimezone);
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
        assertNotSystemUser(user);
        if (!Objects.equals(request.version(), user.getOptLockVersion())) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }

        String nextMobile = request.mobileNumber() != null ? validateMobileNumber(request.mobileNumber()) : null;
        String nextLocale = request.locale() != null ? normalizeLocaleForUpdate(request.locale()) : null;
        String nextTimezone = request.timezone() != null ? validateTimezone(request.timezone()) : null;
        Map<String, Object> nextAttributes = request.attributes() != null
                ? sanitizeJsonMap(request.attributes(), "attributes")
                : null;

        long nextSyncVersion = nextSyncVersion(user);
        // Canonical lock order: tp_keycloak_outbox -> tp_user (enqueue BEFORE mutating Hibernate-managed UserEntity)
        outboxService.enqueueUserReconcile(user.getId(), nextSyncVersion);

        if (request.firstName() != null) {
            user.setFirstName(trimToNull(request.firstName()));
        }
        if (request.lastName() != null) {
            user.setLastName(trimToNull(request.lastName()));
        }
        if (request.mobileNumber() != null) {
            user.setMobileNumber(nextMobile);
        }
        if (request.department() != null) {
            user.setDepartment(trimToNull(request.department()));
        }
        if (request.locale() != null) {
            user.setLocale(nextLocale);
        }
        if (request.timezone() != null) {
            user.setTimezone(nextTimezone);
        }
        if (request.attributes() != null) {
            user.setAttributes(nextAttributes);
        }

        user.setSyncVersion(nextSyncVersion);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(user);
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
        return deactivateUserPhaseATx(id, null, caller);
    }

    @Transactional
    public DeactivatePhaseAResult deactivateUserPhaseATx(Long id, Long expectedVersion, CallerPrincipal caller) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "User '" + id + "' was not found."
                ));
        assertNotSystemUser(user);
        if (expectedVersion != null && !Objects.equals(expectedVersion, user.getOptLockVersion())) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }
        long nextSyncVersion = nextSyncVersion(user);
        // Canonical lock order: tp_keycloak_outbox -> tp_user
        outboxService.enqueueUserReconcile(user.getId(), nextSyncVersion);

        user.setStatus(UserStatus.DISABLED);
        user.setSyncVersion(nextSyncVersion);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(user);
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
    public CasCompletionResult markRevocationSyncedTx(
            Long outboxId,
            UUID leaseToken,
            Long userId,
            String keycloakSub,
            long syncVersion,
            boolean wasReclaimed) {
        return outboxService.completeUserRevocationSync(
                outboxId,
                leaseToken,
                userId,
                syncVersion,
                keycloakSub,
                wasReclaimed
        );
    }

    @Transactional
    public UserResponse reactivateUserTx(Long id, CallerPrincipal caller) {
        return reactivateUserTx(id, null, caller);
    }

    @Transactional
    public UserResponse reactivateUserTx(Long id, Long expectedVersion, CallerPrincipal caller) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "User '" + id + "' was not found."
                ));
        assertNotSystemUser(user);
        if (expectedVersion != null && !Objects.equals(expectedVersion, user.getOptLockVersion())) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }
        if (user.getStatus() != UserStatus.DISABLED) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "User '" + id + "' cannot be reactivated from status " + user.getStatus() + "."
            );
        }

        long nextSyncVersion = nextSyncVersion(user);
        // Canonical lock order: tp_keycloak_outbox -> tp_user
        outboxService.enqueueUserReconcile(user.getId(), nextSyncVersion);

        user.setStatus(UserStatus.ACTIVE);
        user.setSyncVersion(nextSyncVersion);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setUpdatedBy(caller.subject());

        UserEntity saved = userRepository.saveAndFlush(user);
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

        String nextMobile = request.mobileNumber() != null ? validateMobileNumber(request.mobileNumber()) : null;
        String nextTimezone = request.timezone() != null ? validateTimezone(request.timezone()) : null;
        String nextLocale = request.locale() != null ? normalizeLocaleForUpdate(request.locale()) : null;
        boolean localeChanged = nextLocale != null && !Objects.equals(nextLocale, user.getLocale());
        Map<String, Object> nextSelfPrefs = request.selfServicePreferences() != null
                ? sanitizeJsonMap(request.selfServicePreferences(), "selfServicePreferences")
                : null;

        boolean keycloakSyncedChanged = localeChanged || firstNameChanged || lastNameChanged;
        long nextSyncVersion = nextSyncVersion(user);
        if (keycloakSyncedChanged) {
            // Canonical lock order: tp_keycloak_outbox -> tp_user
            outboxService.enqueueUserReconcile(user.getId(), nextSyncVersion);
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
            user.setMobileNumber(nextMobile);
        }
        if (request.timezone() != null) {
            user.setTimezone(nextTimezone);
        }
        if (request.locale() != null) {
            user.setLocale(nextLocale);
        }
        if (request.selfServicePreferences() != null) {
            user.setSelfServicePreferences(nextSelfPrefs);
        }
        if (keycloakSyncedChanged) {
            user.setSyncVersion(nextSyncVersion);
            user.setSyncStatus(SyncStatus.PENDING_SYNC);
        }
        user.setUpdatedBy(keycloakSub);

        UserEntity saved = userRepository.saveAndFlush(user);
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

    static void assertNotSystemUser(UserEntity user) {
        if (user != null && "system".equalsIgnoreCase(user.getUserName())) {
            throw new ApiException(
                    ApiErrorCode.ACCESS_DENIED,
                    "The seeded system user cannot be modified, disabled, or reactivated."
            );
        }
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

    private static String validateTimezone(String rawTimezone) {
        if (rawTimezone == null) {
            return null;
        }
        if (rawTimezone.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "timezone must not be blank.");
        }
        String trimmed = rawTimezone.trim();
        if (trimmed.length() > 64) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "timezone must not exceed 64 characters.");
        }
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (DateTimeException ex) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "timezone must be a valid IANA time zone ID (e.g., UTC, America/New_York)."
            );
        }
    }

    private static String validateMobileNumber(String rawMobile) {
        if (rawMobile == null) {
            return null;
        }
        if (rawMobile.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "mobileNumber must not be blank.");
        }
        String trimmed = rawMobile.trim();
        if (trimmed.length() > 32 || !MOBILE_NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "mobileNumber must be a valid phone number without control characters."
            );
        }
        int digitCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digitCount++;
            }
        }
        if (digitCount < 5) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "mobileNumber must contain at least 5 digits."
            );
        }
        return trimmed;
    }

    private static List<String> validateGroupsFailClosed(List<String> rawGroups) {
        List<String> normalized = normalizeStringList(rawGroups, "groups");
        if (normalized != null && !normalized.isEmpty()) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "groups assignment is not supported until Phase 4B group governance is enabled."
            );
        }
        return normalized;
    }

    private static List<String> normalizeStringList(List<String> rawItems, String fieldName) {
        if (rawItems == null) {
            return null;
        }
        if (rawItems.size() > MAX_ROLE_GROUP_LIST_SIZE) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " must not exceed " + MAX_ROLE_GROUP_LIST_SIZE + " items."
            );
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String item : rawItems) {
            if (item == null || item.isBlank()) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " must not contain null or blank entries."
                );
            }
            String trimmed = item.trim();
            if (trimmed.length() > MAX_ROLE_GROUP_NAME_LENGTH) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " entries must not exceed " + MAX_ROLE_GROUP_NAME_LENGTH + " characters."
                );
            }
            deduplicated.add(trimmed);
        }
        return new ArrayList<>(deduplicated);
    }

    private static Map<String, Object> sanitizeJsonMap(Map<String, Object> rawMap, String fieldName) {
        if (rawMap == null || rawMap.isEmpty()) {
            return new LinkedHashMap<>();
        }
        int[] totalNodes = {0};
        Map<String, Object> copy = copyAndValidateMap(rawMap, fieldName, 0, totalNodes);
        try {
            byte[] serialized = JSON_SIZE_MAPPER.writeValueAsBytes(copy);
            if (serialized.length > MAX_JSON_MAP_BYTES) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " exceeds maximum serialized size of " + MAX_JSON_MAP_BYTES + " bytes."
                );
            }
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " contains an unserializable value."
            );
        }
        return copy;
    }

    private static Map<String, Object> copyAndValidateMap(
            Map<?, ?> source, String fieldName, int depth, int[] totalNodes) {
        if (depth > MAX_MAP_DEPTH) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " exceeds maximum allowed nesting depth."
            );
        }
        if (source.size() > MAX_MAP_ENTRIES) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " exceeds maximum allowed map entries (" + MAX_MAP_ENTRIES + ")."
            );
        }
        Map<String, Object> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String rawKey) || rawKey.isBlank()) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " keys must be non-blank strings."
                );
            }
            String trimmedKey = rawKey.trim();
            if (trimmedKey.length() > MAX_JSON_KEY_LENGTH) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " keys must not exceed " + MAX_JSON_KEY_LENGTH + " characters."
                );
            }
            incrementNodeCount(fieldName, totalNodes);
            copy.put(trimmedKey, copyAndValidateValue(entry.getValue(), fieldName, depth + 1, totalNodes));
        }
        return copy;
    }

    private static Object copyAndValidateValue(
            Object value, String fieldName, int depth, int[] totalNodes) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String str) {
            if (str.length() > MAX_JSON_STRING_LENGTH) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " string values must not exceed " + MAX_JSON_STRING_LENGTH + " characters."
                );
            }
            return str;
        }
        if (value instanceof Number) {
            return value;
        }
        if (depth > MAX_MAP_DEPTH) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " exceeds maximum allowed nesting depth."
            );
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return copyAndValidateMap(nestedMap, fieldName, depth, totalNodes);
        }
        if (value instanceof List<?> nestedList) {
            if (nestedList.size() > MAX_LIST_ITEMS) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_FAILED,
                        fieldName + " exceeds maximum allowed list length (" + MAX_LIST_ITEMS + ")."
                );
            }
            List<Object> copied = new ArrayList<>(nestedList.size());
            for (Object item : nestedList) {
                incrementNodeCount(fieldName, totalNodes);
                copied.add(copyAndValidateValue(item, fieldName, depth + 1, totalNodes));
            }
            return copied;
        }
        throw new ApiException(
                ApiErrorCode.VALIDATION_FAILED,
                fieldName + " contains an unsupported value type."
        );
    }

    private static void incrementNodeCount(String fieldName, int[] totalNodes) {
        if (++totalNodes[0] > MAX_TOTAL_NODES) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    fieldName + " exceeds maximum allowed node count (" + MAX_TOTAL_NODES + ")."
            );
        }
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
