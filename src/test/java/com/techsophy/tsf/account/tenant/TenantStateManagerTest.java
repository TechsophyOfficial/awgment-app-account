package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantStateManagerTest {

    private TenantRepository tenantRepository;
    private IdGeneratorImpl idGenerator;
    private TenantStateManager stateManager;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        idGenerator = mock(IdGeneratorImpl.class);
        stateManager = new TenantStateManager(tenantRepository, idGenerator);
    }

    @Test
    void beginProvisioningShouldCreateProvisioningEntityAndRejectDuplicates() {
        CreateTenantRequest request = new CreateTenantRequest("acme", "  Acme Corp  ", null, null, null);
        when(tenantRepository.existsByTenantId("acme")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(501L));
        when(tenantRepository.saveAndFlush(any(TenantEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantEntity created = stateManager.beginProvisioning(request, "platform-sub");
        assertThat(created.getId()).isEqualTo(501L);
        assertThat(created.getTenantId()).isEqualTo("acme");
        assertThat(created.getSchemaName()).isEqualTo("tenant_acme");
        assertThat(created.getDisplayName()).isEqualTo("Acme Corp");
        assertThat(created.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(created.getCreatedBy()).isEqualTo("platform-sub");

        when(tenantRepository.existsByTenantId("acme")).thenReturn(true);
        assertThatThrownBy(() -> stateManager.beginProvisioning(request, "platform-sub"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.DUPLICATE_RESOURCE));

        when(tenantRepository.existsByTenantId("acme")).thenReturn(false);
        when(tenantRepository.saveAndFlush(any(TenantEntity.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));
        assertThatThrownBy(() -> stateManager.beginProvisioning(request, "platform-sub"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.DUPLICATE_RESOURCE));
    }

    @Test
    void beginRetryProvisioningShouldCheckExistenceAndFailedState() {
        when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stateManager.beginRetryProvisioning("missing", "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        TenantEntity active = new TenantEntity();
        active.setTenantId("acme");
        active.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> stateManager.beginRetryProvisioning("acme", "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION));

        TenantEntity failed = new TenantEntity();
        failed.setTenantId("acme");
        failed.setStatus(TenantStatus.PROVISIONING_FAILED);
        failed.setLastError("previous error");
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(failed));
        when(tenantRepository.saveAndFlush(failed)).thenReturn(failed);

        TenantEntity retried = stateManager.beginRetryProvisioning("acme", "actor");
        assertThat(retried.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(retried.getLastError()).isNull();
        assertThat(retried.getUpdatedBy()).isEqualTo("actor");
    }

    @Test
    void markActiveMarkFailedAndUpdateStatusShouldHandleAllBranches() {
        when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stateManager.markActive("missing", "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        stateManager.markProvisioningFailed("missing", "err", "actor");
        verify(tenantRepository, never()).saveAndFlush(any());

        TenantEntity entity = new TenantEntity();
        entity.setTenantId("acme");
        entity.setStatus(TenantStatus.PROVISIONING);
        entity.setOptLockVersion(1L);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(entity));
        when(tenantRepository.saveAndFlush(entity)).thenReturn(entity);

        stateManager.markProvisioningFailed("acme", "boom", "actor");
        assertThat(entity.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
        assertThat(entity.getLastError()).isEqualTo("boom");

        TenantEntity activated = stateManager.markActive("acme", "actor");
        assertThat(activated.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(activated.getLastError()).isNull();

        assertThatThrownBy(() -> stateManager.updateStatus("missing", TenantStatus.SUSPENDED, 0L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> stateManager.updateStatus("acme", TenantStatus.SUSPENDED, 99L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));

        TenantEntity suspended = stateManager.updateStatus("acme", TenantStatus.SUSPENDED, 1L, "actor");
        assertThat(suspended.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }
}
