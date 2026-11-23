package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantStateManagerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-23T12:00:00Z");

    private TenantRepository tenantRepository;
    private IdGeneratorImpl idGenerator;
    private Clock fixedClock;
    private TenantStateManager stateManager;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        idGenerator = mock(IdGeneratorImpl.class);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        stateManager = new TenantStateManager(tenantRepository, idGenerator, 300L, fixedClock);
    }

    @Test
    void beginProvisioningShouldPersistPendingAdminFieldsAndRejectDuplicates() {
        String adminEmail = "Approver" + "@" + "acme.invalid";
        CreateTenantRequest request = new CreateTenantRequest(
                "acme", "  Acme Corp  ", "  " + adminEmail + " ", "  Acme ", " Approver ");
        when(tenantRepository.existsByTenantId("acme")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(501L));
        when(tenantRepository.saveAndFlush(any(TenantEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantEntity created = stateManager.beginProvisioning(request, "platform-sub");
        assertThat(created.getId()).isEqualTo(501L);
        assertThat(created.getTenantId()).isEqualTo("acme");
        assertThat(created.getSchemaName()).isEqualTo("tenant_acme");
        assertThat(created.getDisplayName()).isEqualTo("Acme Corp");
        assertThat(created.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(created.getPendingAdminEmail()).isEqualTo("approver" + "@" + "acme.invalid");
        assertThat(created.getPendingAdminFirstName()).isEqualTo("Acme");
        assertThat(created.getPendingAdminLastName()).isEqualTo("Approver");
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
    void beginRetryProvisioningShouldReclaimStaleProvisioningRejectFreshProvisioningAndHandleConcurrency() {
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

        // Fresh PROVISIONING (100s old < 300s TTL) must be rejected with 409 INVALID_STATE_TRANSITION
        TenantEntity freshProvisioning = new TenantEntity();
        freshProvisioning.setTenantId("acme");
        freshProvisioning.setStatus(TenantStatus.PROVISIONING);
        freshProvisioning.setUpdatedAt(FIXED_NOW.minusSeconds(100));
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(freshProvisioning));
        assertThatThrownBy(() -> stateManager.beginRetryProvisioning("acme", "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION));

        // Stale PROVISIONING (301s old > 300s TTL) must be reclaimed cleanly
        TenantEntity staleProvisioning = new TenantEntity();
        staleProvisioning.setTenantId("acme");
        staleProvisioning.setStatus(TenantStatus.PROVISIONING);
        staleProvisioning.setOptLockVersion(1L);
        staleProvisioning.setUpdatedAt(FIXED_NOW.minusSeconds(301));
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(staleProvisioning));
        when(tenantRepository.saveAndFlush(staleProvisioning)).thenReturn(staleProvisioning);

        TenantEntity reclaimed = stateManager.beginRetryProvisioning("acme", "actor");
        assertThat(reclaimed.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(reclaimed.getUpdatedAt()).isEqualTo(FIXED_NOW);

        // Concurrent retry race throwing OptimisticLockingFailureException maps to 409 CONCURRENT_MODIFICATION
        staleProvisioning.setUpdatedAt(FIXED_NOW.minusSeconds(301));
        when(tenantRepository.saveAndFlush(staleProvisioning))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("race"));
        assertThatThrownBy(() -> stateManager.beginRetryProvisioning("acme", "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));

        // PROVISIONING_FAILED transitions cleanly to PROVISIONING
        TenantEntity failed = new TenantEntity();
        failed.setTenantId("acme");
        failed.setStatus(TenantStatus.PROVISIONING_FAILED);
        failed.setLastError("previous error");
        failed.setOptLockVersion(2L);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(failed));
        when(tenantRepository.saveAndFlush(failed)).thenReturn(failed);

        TenantEntity retried = stateManager.beginRetryProvisioning("acme", "actor");
        assertThat(retried.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(retried.getLastError()).isNull();
        assertThat(retried.getUpdatedBy()).isEqualTo("actor");
    }

    @Test
    void markActiveMarkFailedAndQuarantineShouldEnforceVersionFencingAndClearPendingAdmin() {
        when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stateManager.markActive("missing", 0L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        stateManager.markProvisioningFailed("missing", 0L, "err", "actor");
        verify(tenantRepository, never()).saveAndFlush(any());

        // Stale worker calling markProvisioningFailed with mismatched version MUST be a no-op
        TenantEntity activeEntity = new TenantEntity();
        activeEntity.setTenantId("acme");
        activeEntity.setStatus(TenantStatus.ACTIVE);
        activeEntity.setOptLockVersion(3L);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(activeEntity));
        stateManager.markProvisioningFailed("acme", 1L, "late-worker-err", "actor");
        verify(tenantRepository, never()).saveAndFlush(any());
        assertThat(activeEntity.getStatus()).isEqualTo(TenantStatus.ACTIVE);

        // Stale worker calling markActive with wrong version or non-PROVISIONING state MUST throw CONCURRENT_MODIFICATION
        assertThatThrownBy(() -> stateManager.markActive("acme", 1L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));

        // Proper fenced markProvisioningFailed & markActive clear pending admin on ACTIVE
        TenantEntity provisioning = new TenantEntity();
        provisioning.setTenantId("acme");
        provisioning.setStatus(TenantStatus.PROVISIONING);
        provisioning.setOptLockVersion(1L);
        provisioning.setPendingAdminEmail("approver" + "@" + "acme.invalid");
        provisioning.setPendingAdminFirstName("Acme");
        provisioning.setPendingAdminLastName("Admin");
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(provisioning));
        when(tenantRepository.saveAndFlush(provisioning)).thenReturn(provisioning);

        stateManager.markProvisioningFailed("acme", 1L, "boom", "actor");
        assertThat(provisioning.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
        assertThat(provisioning.getLastError()).isEqualTo("boom");
        assertThat(provisioning.getPendingAdminEmail()).isEqualTo("approver" + "@" + "acme.invalid");

        provisioning.setStatus(TenantStatus.PROVISIONING);
        TenantEntity activated = stateManager.markActive("acme", 1L, "actor");
        assertThat(activated.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(activated.getLastError()).isNull();
        assertThat(activated.getPendingAdminEmail()).isNull();
        assertThat(activated.getPendingAdminFirstName()).isNull();
        assertThat(activated.getPendingAdminLastName()).isNull();

        // quarantineActiveTenant marks ACTIVE -> PROVISIONING_FAILED and ignores non-ACTIVE
        stateManager.quarantineActiveTenant("acme", "startup migration failed", "system");
        assertThat(activated.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
        assertThat(activated.getLastError()).isEqualTo("startup migration failed");

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

    @Test
    void shouldHandleNullUpdatedAtBlankPendingAdminAndOptimisticLockingBranches() {
        CreateTenantRequest blankOptionals = new CreateTenantRequest("acme", "Acme Corp", "  ", null, " ");
        when(tenantRepository.existsByTenantId("acme")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(777L));
        when(tenantRepository.saveAndFlush(any(TenantEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantEntity created = stateManager.beginProvisioning(blankOptionals, "actor");
        assertThat(created.getPendingAdminEmail()).isNull();
        assertThat(created.getPendingAdminFirstName()).isNull();
        assertThat(created.getPendingAdminLastName()).isNull();

        // PROVISIONING with null updatedAt is treated as stale
        created.setOptLockVersion(1L);
        created.setUpdatedAt(null);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(created));
        assertThat(stateManager.beginRetryProvisioning("acme", "actor").getUpdatedAt()).isEqualTo(FIXED_NOW);

        // markActive rejects version mismatch on PROVISIONING and maps OptimisticLockingFailureException
        assertThatThrownBy(() -> stateManager.markActive("acme", 99L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));

        when(tenantRepository.saveAndFlush(created))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("race"));
        assertThatThrownBy(() -> stateManager.markActive("acme", 1L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));

        // markProvisioningFailed & quarantineActiveTenant swallow OptimisticLockingFailureException safely
        stateManager.markProvisioningFailed("acme", 1L, "err", "actor");

        // quarantineActiveTenant ignores non-ACTIVE status without save, and catches OptimisticLockingFailureException when ACTIVE
        org.mockito.Mockito.clearInvocations(tenantRepository);
        created.setStatus(TenantStatus.SUSPENDED);
        stateManager.quarantineActiveTenant("acme", "ignored", "system");
        verify(tenantRepository, never()).saveAndFlush(any());

        created.setStatus(TenantStatus.ACTIVE);
        stateManager.quarantineActiveTenant("acme", "err", "system");

        assertThatThrownBy(() -> stateManager.updateStatus("acme", TenantStatus.SUSPENDED, 1L, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));
    }
}
