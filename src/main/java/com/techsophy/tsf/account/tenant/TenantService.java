package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.keycloak.KeycloakRealmClient;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractVerifier;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.security.TenantAuthenticationManagerResolver;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.tenant.dto.TenantResponse;
import com.techsophy.tsf.account.tenant.dto.UpdateTenantStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates tenant provisioning, retry recovery, listing, suspension, and reactivation
 * across PostgreSQL schemas and Keycloak 26 realms (UC-TENANT-01..04).
 */
@Service
public class TenantService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "tenantId",
            "displayName",
            "status",
            "createdAt",
            "updatedAt"
    );

    private final TenantRepository tenantRepository;
    private final TenantStateManager tenantStateManager;
    private final TenantFlywayMigrator tenantFlywayMigrator;
    private final TenantSeeder tenantSeeder;
    private final KeycloakRealmClient keycloakRealmClient;
    private final KeycloakRealmContractVerifier contractVerifier;
    private final TenantLookupService tenantLookupService;
    private final TenantAuthenticationManagerResolver authManagerResolver;
    private final SecurityAuditService securityAuditService;

    public TenantService(
            TenantRepository tenantRepository,
            TenantStateManager tenantStateManager,
            TenantFlywayMigrator tenantFlywayMigrator,
            TenantSeeder tenantSeeder,
            KeycloakRealmClient keycloakRealmClient,
            KeycloakRealmContractVerifier contractVerifier,
            TenantLookupService tenantLookupService,
            TenantAuthenticationManagerResolver authManagerResolver,
            SecurityAuditService securityAuditService) {
        this.tenantRepository = tenantRepository;
        this.tenantStateManager = tenantStateManager;
        this.tenantFlywayMigrator = tenantFlywayMigrator;
        this.tenantSeeder = tenantSeeder;
        this.keycloakRealmClient = keycloakRealmClient;
        this.contractVerifier = contractVerifier;
        this.tenantLookupService = tenantLookupService;
        this.authManagerResolver = authManagerResolver;
        this.securityAuditService = securityAuditService;
    }

    public TenantResponse createTenant(CreateTenantRequest request, String actorSub) {
        String tenantId = request.tenantId();
        TenantIdValidator.validate(tenantId);
        TenantContext.clear();

        tenantStateManager.beginProvisioning(request, actorSub);
        return executeProvisioningPipeline(
                tenantId,
                request.bootstrapAdminEmail(),
                request.bootstrapAdminFirstName(),
                request.bootstrapAdminLastName(),
                actorSub
        );
    }

    public TenantResponse retryProvisioning(String tenantId, String actorSub) {
        TenantIdValidator.validate(tenantId);
        TenantContext.clear();

        tenantStateManager.beginRetryProvisioning(tenantId, actorSub);
        return executeProvisioningPipeline(tenantId, null, null, null, actorSub);
    }

    public PageResponse<TenantResponse> listTenants(TenantStatus status, int page, int size, String sort) {
        if (page < 0) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Page index must be >= 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Sort parsedSort = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, parsedSort);
        TenantContext.clear();

        Page<TenantEntity> entityPage = status != null
                ? tenantRepository.findByStatus(status, pageable)
                : tenantRepository.findAll(pageable);
        return PageResponse.from(entityPage.map(entity -> toResponse(entity, entity.getStatus() == TenantStatus.ACTIVE)));
    }

    public TenantResponse updateTenantStatus(
            String tenantId,
            UpdateTenantStatusRequest request,
            String actorSub) {
        TenantIdValidator.validate(tenantId);
        TenantContext.clear();

        TenantStatus targetStatus = request.status();
        if (targetStatus != TenantStatus.ACTIVE && targetStatus != TenantStatus.SUSPENDED) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Target tenant status must be ACTIVE or SUSPENDED."
            );
        }

        TenantEntity existing = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' was not found."
                ));

        if (!Objects.equals(request.version(), existing.getOptLockVersion())) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }

        TenantStatus currentStatus = existing.getStatus();
        if (currentStatus == targetStatus
                || (currentStatus != TenantStatus.ACTIVE && currentStatus != TenantStatus.SUSPENDED)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot transition tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId)
                            + "' from " + currentStatus + " to " + targetStatus + "."
            );
        }

        if (targetStatus == TenantStatus.SUSPENDED) {
            return suspendTenant(tenantId, request.version(), actorSub);
        }
        return reactivateTenant(tenantId, request.version(), actorSub);
    }

    private TenantResponse executeProvisioningPipeline(
            String tenantId,
            String bootstrapAdminEmail,
            String bootstrapAdminFirstName,
            String bootstrapAdminLastName,
            String actorSub) {
        try {
            tenantFlywayMigrator.provisionAndMigrateTenantSchema(tenantId);
            tenantSeeder.seedTenant(tenantId, bootstrapAdminEmail, bootstrapAdminFirstName, bootstrapAdminLastName);
            KeycloakRealmContractSnapshot snapshot = keycloakRealmClient.fetchRealmContract(tenantId);
            contractVerifier.verify(snapshot);

            TenantEntity activated = tenantStateManager.markActive(tenantId, actorSub);
            tenantLookupService.evict(tenantId);
            securityAuditService.record(
                    "TENANT_PROVISION",
                    "TENANT",
                    tenantId,
                    AuditOutcome.SUCCESS,
                    actorSub,
                    null,
                    Map.of("schemaName", activated.getSchemaName())
            );
            return toResponse(activated, true);
        } catch (RuntimeException ex) {
            String safeError = truncateError(ex.getMessage());
            tenantStateManager.markProvisioningFailed(tenantId, safeError, actorSub);
            tenantLookupService.evict(tenantId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (ex instanceof ApiException apiEx) {
                metadata.put("errorCode", apiEx.errorCode().name());
            } else {
                metadata.put("error", ex.getClass().getSimpleName());
            }
            securityAuditService.record(
                    "TENANT_PROVISION",
                    "TENANT",
                    tenantId,
                    AuditOutcome.FAILURE,
                    actorSub,
                    null,
                    metadata
            );
            throw ex;
        }
    }

    private TenantResponse suspendTenant(String tenantId, Long expectedVersion, String actorSub) {
        TenantEntity suspended = tenantStateManager.updateStatus(
                tenantId, TenantStatus.SUSPENDED, expectedVersion, actorSub);
        evictTenantCaches(tenantId);
        try {
            keycloakRealmClient.setRealmEnabled(tenantId, false);
        } catch (RuntimeException ex) {
            securityAuditService.record(
                    "TENANT_SUSPEND",
                    "TENANT",
                    tenantId,
                    AuditOutcome.FAILURE,
                    actorSub,
                    null,
                    Map.of("realmDisabled", false)
            );
            if (ex instanceof ApiException apiEx) {
                throw apiEx;
            }
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Tenant marked SUSPENDED locally, but failed to disable Keycloak realm."
            );
        }
        securityAuditService.record(
                "TENANT_SUSPEND",
                "TENANT",
                tenantId,
                AuditOutcome.SUCCESS,
                actorSub,
                null,
                Map.of("schemaName", suspended.getSchemaName())
        );
        return toResponse(suspended, false);
    }

    private TenantResponse reactivateTenant(String tenantId, Long expectedVersion, String actorSub) {
        tenantFlywayMigrator.provisionAndMigrateTenantSchema(tenantId);
        keycloakRealmClient.setRealmEnabled(tenantId, true);
        try {
            KeycloakRealmContractSnapshot snapshot = keycloakRealmClient.fetchRealmContract(tenantId);
            contractVerifier.verify(snapshot);
        } catch (RuntimeException ex) {
            try {
                keycloakRealmClient.setRealmEnabled(tenantId, false);
            } catch (RuntimeException suppressed) {
                ex.addSuppressed(suppressed);
            }
            evictTenantCaches(tenantId);
            securityAuditService.record(
                    "TENANT_REACTIVATE",
                    "TENANT",
                    tenantId,
                    AuditOutcome.FAILURE,
                    actorSub,
                    null,
                    Map.of("reason", "KEYCLOAK_CONTRACT_OR_UPSTREAM_FAILED")
            );
            if (ex instanceof ApiException apiEx) {
                throw apiEx;
            }
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Tenant reactivation failed during Keycloak realm contract verification."
            );
        }

        TenantEntity reactivated = tenantStateManager.updateStatus(
                tenantId, TenantStatus.ACTIVE, expectedVersion, actorSub);
        evictTenantCaches(tenantId);
        securityAuditService.record(
                "TENANT_REACTIVATE",
                "TENANT",
                tenantId,
                AuditOutcome.SUCCESS,
                actorSub,
                null,
                Map.of("schemaName", reactivated.getSchemaName())
        );
        return toResponse(reactivated, true);
    }

    private void evictTenantCaches(String tenantId) {
        tenantLookupService.evict(tenantId);
        authManagerResolver.evictTenant(tenantId);
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Sort parameter must not be blank.");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Invalid sort format.");
        }
        String property = parts[0].trim();
        if (!ALLOWED_SORT_PROPERTIES.contains(property)) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Unsupported sort property.");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String rawDir = parts[1].trim();
            if ("desc".equalsIgnoreCase(rawDir)) {
                direction = Sort.Direction.DESC;
            } else if (!"asc".equalsIgnoreCase(rawDir)) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Sort direction must be 'asc' or 'desc'.");
            }
        }
        return Sort.by(direction, property);
    }

    private static String truncateError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Provisioning failed";
        }
        String collapsed = raw.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", "_").trim();
        return collapsed.length() > MAX_ERROR_LENGTH ? collapsed.substring(0, MAX_ERROR_LENGTH) : collapsed;
    }

    private static TenantResponse toResponse(TenantEntity entity, boolean keycloakContractVerified) {
        return new TenantResponse(
                entity.getTenantId(),
                entity.getSchemaName(),
                entity.getDisplayName(),
                entity.getStatus(),
                keycloakContractVerified,
                entity.getLastError(),
                entity.getOptLockVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
