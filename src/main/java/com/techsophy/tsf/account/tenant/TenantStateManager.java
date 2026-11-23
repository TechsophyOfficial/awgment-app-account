package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Isolated {@code REQUIRES_NEW} transaction boundary for persisting {@link TenantEntity} state
 * transitions so {@code PROVISIONING} and {@code PROVISIONING_FAILED} are durably committed even
 * when downstream DDL or Keycloak verification fails.
 */
@Component
public class TenantStateManager {

    private final TenantRepository tenantRepository;
    private final IdGeneratorImpl idGenerator;

    public TenantStateManager(TenantRepository tenantRepository, IdGeneratorImpl idGenerator) {
        this.tenantRepository = tenantRepository;
        this.idGenerator = idGenerator;
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
        Instant now = Instant.now();
        TenantEntity entity = new TenantEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setTenantId(tenantId);
        entity.setSchemaName(TenantIdValidator.toSchemaName(tenantId));
        entity.setDisplayName(request.displayName().trim());
        entity.setStatus(TenantStatus.PROVISIONING);
        entity.setLastError(null);
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
        if (entity.getStatus() != TenantStatus.PROVISIONING_FAILED) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' is in status "
                            + entity.getStatus() + "; retry-provisioning requires PROVISIONING_FAILED."
            );
        }
        entity.setStatus(TenantStatus.PROVISIONING);
        entity.setLastError(null);
        entity.setUpdatedBy(actorSub);
        entity.setUpdatedAt(Instant.now());
        return tenantRepository.saveAndFlush(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantEntity markActive(String tenantId, String actorSub) {
        TenantEntity entity = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> notFound(tenantId));
        entity.setStatus(TenantStatus.ACTIVE);
        entity.setLastError(null);
        entity.setUpdatedBy(actorSub);
        entity.setUpdatedAt(Instant.now());
        return tenantRepository.saveAndFlush(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProvisioningFailed(String tenantId, String errorMessage, String actorSub) {
        tenantRepository.findByTenantId(tenantId).ifPresent(entity -> {
            entity.setStatus(TenantStatus.PROVISIONING_FAILED);
            entity.setLastError(errorMessage);
            entity.setUpdatedBy(actorSub);
            entity.setUpdatedAt(Instant.now());
            tenantRepository.saveAndFlush(entity);
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
        entity.setUpdatedAt(Instant.now());
        return tenantRepository.saveAndFlush(entity);
    }

    private static ApiException notFound(String tenantId) {
        return new ApiException(
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "' was not found."
        );
    }
}
