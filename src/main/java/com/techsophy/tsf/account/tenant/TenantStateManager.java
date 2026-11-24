package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Isolated {@code REQUIRES_NEW} transaction boundary for persisting {@link TenantEntity} state
 * transitions with version fencing so {@code PROVISIONING} and {@code PROVISIONING_FAILED}
 * are durably committed and protected against late/stale worker overwrites.
 */
@Slf4j
@Component
public class TenantStateManager {

    private static final long MIN_STALE_TTL_SECONDS = 30L;

    private final TenantRepository tenantRepository;
    private final IdGeneratorImpl idGenerator;
    private final Duration provisioningStaleTtl;
    private final Clock clock;

    @Autowired
    public TenantStateManager(
            TenantRepository tenantRepository,
            IdGeneratorImpl idGenerator,
            @Value("${awgment.multitenancy.provisioning-stale-ttl-seconds:300}") long provisioningStaleTtlSeconds) {
        this(tenantRepository, idGenerator, provisioningStaleTtlSeconds, Clock.systemUTC());
    }

    TenantStateManager(
            TenantRepository tenantRepository,
            IdGeneratorImpl idGenerator,
            long provisioningStaleTtlSeconds,
            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.idGenerator = idGenerator;
        this.provisioningStaleTtl = Duration.ofSeconds(Math.max(provisioningStaleTtlSeconds, MIN_STALE_TTL_SECONDS));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantEntity beginProvisioning(CreateTenantRequest request, String actorSub) {
        String tenantId = request.tenantId();
        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new ApiException(
                    ApiErrorCode.DUPLICATE_RESOURCE,
                    "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' already exists."
            );
        }
        Instant now = clock.instant();
        TenantEntity entity = new TenantEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setTenantId(tenantId);
        entity.setSchemaName(TenantIdValidator.toSchemaName(tenantId));
        entity.setDisplayName(request.displayName().trim());
        entity.setStatus(TenantStatus.PROVISIONING);
        entity.setLastError(null);
        entity.setPendingAdminEmail(
                TenantSeeder.normalizeAndValidateBootstrapAdminEmail(tenantId, request.bootstrapAdminEmail()));
        entity.setPendingAdminFirstName(trimToNull(request.bootstrapAdminFirstName()));
        entity.setPendingAdminLastName(trimToNull(request.bootstrapAdminLastName()));
        entity.setCreatedBy(actorSub);
        entity.setUpdatedBy(actorSub);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            return tenantRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(
                    ApiErrorCode.DUPLICATE_RESOURCE,
                    "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' already exists."
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantEntity beginRetryProvisioning(String tenantId, String actorSub) {
        TenantEntity entity = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> notFound(tenantId));
        Instant now = clock.instant();
        boolean isFailed = entity.getStatus() == TenantStatus.PROVISIONING_FAILED;
        boolean isStaleProvisioning = entity.getStatus() == TenantStatus.PROVISIONING
                && isProvisioningStale(entity.getUpdatedAt(), now);
        if (!isFailed && !isStaleProvisioning) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' is in status "
                            + entity.getStatus() + "; retry-provisioning requires PROVISIONING_FAILED or stale PROVISIONING."
            );
        }
        entity.setStatus(TenantStatus.PROVISIONING);
        entity.setLastError(null);
        entity.setUpdatedBy(actorSub);
        entity.setUpdatedAt(now);
        try {
            return tenantRepository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException ex) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantEntity markActive(String tenantId, Long expectedVersion, String actorSub) {
        TenantEntity entity = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> notFound(tenantId));
        if (entity.getStatus() != TenantStatus.PROVISIONING
                || !Objects.equals(entity.getOptLockVersion(), expectedVersion)) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Tenant provisioning state was modified concurrently. Please reload and retry."
            );
        }
        entity.setStatus(TenantStatus.ACTIVE);
        entity.setLastError(null);
        entity.setPendingAdminEmail(null);
        entity.setPendingAdminFirstName(null);
        entity.setPendingAdminLastName(null);
        entity.setUpdatedBy(actorSub);
        entity.setUpdatedAt(clock.instant());
        try {
            return tenantRepository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException ex) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProvisioningFailed(
            String tenantId, Long expectedVersion, String errorMessage, String actorSub) {
        tenantRepository.findByTenantId(tenantId).ifPresent(entity -> {
            if (entity.getStatus() != TenantStatus.PROVISIONING
                    || !Objects.equals(entity.getOptLockVersion(), expectedVersion)) {
                log.warn("Ignoring stale markProvisioningFailed for tenant '{}' (status={}, version={}, expected={})",
                        ProblemDetailFactory.sanitizeForLog(tenantId),
                        entity.getStatus(),
                        entity.getOptLockVersion(),
                        expectedVersion);
                return;
            }
            entity.setStatus(TenantStatus.PROVISIONING_FAILED);
            entity.setLastError(errorMessage);
            entity.setUpdatedBy(actorSub);
            entity.setUpdatedAt(clock.instant());
            try {
                tenantRepository.saveAndFlush(entity);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Concurrent state transition during markProvisioningFailed for tenant '{}'",
                        ProblemDetailFactory.sanitizeForLog(tenantId));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void quarantineActiveTenant(String tenantId, String errorMessage, String actorSub) {
        tenantRepository.findByTenantId(tenantId).ifPresent(entity -> {
            if (entity.getStatus() != TenantStatus.ACTIVE) {
                return;
            }
            entity.setStatus(TenantStatus.PROVISIONING_FAILED);
            entity.setLastError(errorMessage);
            entity.setUpdatedBy(actorSub);
            entity.setUpdatedAt(clock.instant());
            try {
                tenantRepository.saveAndFlush(entity);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Concurrent state transition while quarantining tenant '{}'",
                        ProblemDetailFactory.sanitizeForLog(tenantId));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantEntity updateStatus(
            String tenantId,
            TenantStatus targetStatus,
            Long expectedVersion,
            String actorSub) {
        TenantEntity entity = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> notFound(tenantId));
        if (!Objects.equals(entity.getOptLockVersion(), expectedVersion)) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }
        entity.setStatus(targetStatus);
        entity.setUpdatedBy(actorSub);
        entity.setUpdatedAt(clock.instant());
        try {
            return tenantRepository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException ex) {
            throw new ApiException(
                    ApiErrorCode.CONCURRENT_MODIFICATION,
                    "Resource was modified concurrently. Please reload and retry."
            );
        }
    }

    private boolean isProvisioningStale(Instant updatedAt, Instant now) {
        return updatedAt == null || !updatedAt.plus(provisioningStaleTtl).isAfter(now);
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static ApiException notFound(String tenantId) {
        return new ApiException(
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' was not found."
        );
    }
}
