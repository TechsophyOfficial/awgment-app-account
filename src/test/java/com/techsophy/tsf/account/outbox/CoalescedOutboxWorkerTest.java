package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.keycloak.KeycloakUserSyncClient;
import com.techsophy.tsf.account.keycloak.ReconcileResult;
import com.techsophy.tsf.account.keycloak.UserReconcilePayload;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantEntity;
import com.techsophy.tsf.account.tenant.TenantRepository;
import com.techsophy.tsf.account.tenant.TenantStatus;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.ProvisioningMode;
import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserRepository;
import com.techsophy.tsf.account.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoalescedOutboxWorkerTest {

    private OutboxStore outboxStore;
    private UserRepository userRepository;
    private KeycloakUserSyncClient keycloakUserSyncClient;
    private TenantRepository tenantRepository;
    private OutboxProperties outboxProperties;
    private CoalescedOutboxWorker worker;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);

        outboxStore = mock(OutboxStore.class);
        userRepository = mock(UserRepository.class);
        keycloakUserSyncClient = mock(KeycloakUserSyncClient.class);
        tenantRepository = mock(TenantRepository.class);
        outboxProperties = new OutboxProperties();
        outboxProperties.setBatchSize(20);
        outboxProperties.setLeaseTtlSeconds(60);
        outboxProperties.setPollIntervalMs(2000L);
        outboxProperties.setWorkerId("worker-unit");

        worker = new CoalescedOutboxWorker(
                outboxStore,
                userRepository,
                keycloakUserSyncClient,
                tenantRepository,
                outboxProperties
        );
        outboxService = new OutboxService(outboxStore, worker, outboxProperties, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void pollAllActiveTenantsShouldRespectWorkerFlagIsolateFailuresAndAlwaysClearTenantContext() {
        outboxProperties.setWorkerEnabled(false);
        worker.pollAllActiveTenants();
        verifyNoInteractions(tenantRepository);

        outboxProperties.setWorkerEnabled(true);
        TenantEntity brokenTenant = tenant("broken_t");
        TenantEntity healthyTenant = tenant("acme");
        when(tenantRepository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE))
                .thenReturn(List.of(brokenTenant, healthyTenant));
        when(outboxStore.claimNextJobs(anyInt(), any(Duration.class), anyString()))
                .thenThrow(new IllegalStateException("DB timeout on broken_t"))
                .thenReturn(List.of());

        worker.pollAllActiveTenants();

        verify(outboxStore, times(2)).claimNextJobs(eq(20), eq(Duration.ofSeconds(60)), eq("worker-unit"));
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void processTenantQueueShouldReconcileUserAndTriggerImmediateFollowUpWhenDirty() {
        UUID lease1 = UUID.randomUUID();
        UUID lease2 = UUID.randomUUID();
        KeycloakOutboxEventEntity job1 = reconcileEvent(10L, 501L, lease1);
        KeycloakOutboxEventEntity job2 = reconcileEvent(10L, 501L, lease2);

        when(outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-unit"))
                .thenReturn(List.of(new ClaimedOutboxJob(job1, false)))
                .thenReturn(List.of(new ClaimedOutboxJob(job2, false)))
                .thenReturn(List.of());

        UserEntity userV1 = activeLocalUser(501L, 1L, null);
        UserEntity userV2 = activeLocalUser(501L, 2L, "kc-sub-501");
        when(userRepository.findById(501L))
                .thenReturn(Optional.of(userV1))
                .thenReturn(Optional.of(userV2));

        when(keycloakUserSyncClient.reconcileUser(eq("acme"), any(UserReconcilePayload.class)))
                .thenReturn(new ReconcileResult("kc-sub-501", false));
        when(outboxStore.completeReconcileWithCas(
                10L, lease1, 501L, OutboxAggregateType.USER, 1L, "kc-sub-501", false, false))
                .thenReturn(new CasCompletionResult(true, true));
        when(outboxStore.completeReconcileWithCas(
                10L, lease2, 501L, OutboxAggregateType.USER, 2L, "kc-sub-501", false, false))
                .thenReturn(new CasCompletionResult(true, false));

        int processed = worker.processTenantQueue("acme", 20);
        assertThat(processed).isEqualTo(2);
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
        verify(keycloakUserSyncClient, times(2)).reconcileUser(eq("acme"), any(UserReconcilePayload.class));
    }

    @Test
    void processTenantQueueShouldMarkRetryOnKeycloakFailureAndHandleMissingUser() {
        UUID lease1 = UUID.randomUUID();
        UUID lease2 = UUID.randomUUID();
        KeycloakOutboxEventEntity kcFailJob = reconcileEvent(11L, 502L, lease1);
        KeycloakOutboxEventEntity missingUserJob = reconcileEvent(12L, 999L, lease2);

        when(outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-unit"))
                .thenReturn(List.of(
                        new ClaimedOutboxJob(kcFailJob, false),
                        new ClaimedOutboxJob(missingUserJob, false)));

        when(userRepository.findById(502L)).thenReturn(Optional.of(activeLocalUser(502L, 1L, "kc-502")));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        when(keycloakUserSyncClient.reconcileUser(eq("acme"), any(UserReconcilePayload.class)))
                .thenThrow(new RuntimeException("Keycloak 504 timeout"));

        int processed = worker.processTenantQueue("acme", 20);
        assertThat(processed).isEqualTo(2);

        verify(outboxStore).markRetryOrFailedWithCas(
                11L, lease1, 502L, OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "Keycloak 504 timeout");
        verify(outboxStore).markRetryOrFailedWithCas(
                eq(12L), eq(lease2), eq(999L), eq(OutboxAggregateType.USER), eq(OutboxJobType.RECONCILE_USER), anyString());
    }

    @Test
    void processActionEmailShouldDeferUntilKeycloakSubBoundCancelWhenIneligibleAndRespectDeliveryFlag() {
        UUID leaseWait = UUID.randomUUID();
        UUID leaseDisabled = UUID.randomUUID();
        UUID leaseExternal = UUID.randomUUID();
        UUID leaseReadyDisabledFlag = UUID.randomUUID();
        UUID leaseReadyEnabledFlag = UUID.randomUUID();

        KeycloakOutboxEventEntity waitJob = emailEvent(21L, 601L, leaseWait);
        KeycloakOutboxEventEntity disabledJob = emailEvent(22L, 602L, leaseDisabled);
        KeycloakOutboxEventEntity externalJob = emailEvent(23L, 603L, leaseExternal);
        KeycloakOutboxEventEntity readyPhase2Job = emailEvent(24L, 604L, leaseReadyDisabledFlag);
        KeycloakOutboxEventEntity readyPhase4Job = emailEvent(25L, 605L, leaseReadyEnabledFlag);

        when(userRepository.findById(601L)).thenReturn(Optional.of(activeLocalUser(601L, 1L, null)));
        UserEntity disabledUser = activeLocalUser(602L, 1L, "kc-602");
        disabledUser.setStatus(UserStatus.DISABLED);
        when(userRepository.findById(602L)).thenReturn(Optional.of(disabledUser));
        UserEntity externalUser = activeLocalUser(603L, 1L, "kc-603");
        externalUser.setIdentitySource(IdentitySource.EXTERNAL_IDP);
        when(userRepository.findById(603L)).thenReturn(Optional.of(externalUser));
        when(userRepository.findById(604L)).thenReturn(Optional.of(activeLocalUser(604L, 1L, "kc-604")));
        when(userRepository.findById(605L)).thenReturn(Optional.of(activeLocalUser(605L, 1L, "kc-605")));

        when(outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-unit"))
                .thenReturn(List.of(
                        new ClaimedOutboxJob(waitJob, false),
                        new ClaimedOutboxJob(disabledJob, false),
                        new ClaimedOutboxJob(externalJob, false),
                        new ClaimedOutboxJob(readyPhase2Job, false)));
        when(outboxStore.markActionEmailDispatchedWithCas(24L, leaseReadyDisabledFlag)).thenReturn(true);

        outboxProperties.setActionEmailDeliveryEnabled(false);
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(4);

        verify(outboxStore).deferActionEmailWithCas(eq(21L), eq(leaseWait), any(Duration.class));
        verify(outboxStore).cancelActionEmailWithCas(eq(22L), eq(leaseDisabled), anyString());
        verify(outboxStore).cancelActionEmailWithCas(eq(23L), eq(leaseExternal), anyString());
        verify(outboxStore).markActionEmailDispatchedWithCas(24L, leaseReadyDisabledFlag);
        verify(keycloakUserSyncClient, never()).sendActionEmail(anyString(), anyString(), any(), anyInt());

        // When actionEmailDeliveryEnabled=true, pre-stamps DISPATCHED and records ambiguousDelivery on failure
        outboxProperties.setActionEmailDeliveryEnabled(true);
        when(outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-unit"))
                .thenReturn(List.of(new ClaimedOutboxJob(readyPhase4Job, false)));
        when(outboxStore.markActionEmailDispatchedWithCas(25L, leaseReadyEnabledFlag)).thenReturn(true);
        doThrow(new RuntimeException("SMTP read timeout"))
                .when(keycloakUserSyncClient)
                .sendActionEmail("acme", "kc-605", List.of("UPDATE_PASSWORD"), 3600);

        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(1);
        verify(outboxStore).markActionEmailAmbiguousFailure(25L, "SMTP read timeout");

        // RECONCILE_GROUP + action-email null/malformed payload + stale CAS + preserving caller TenantContext
        UUID leaseGroup = UUID.randomUUID();
        KeycloakOutboxEventEntity groupEvent = new KeycloakOutboxEventEntity();
        groupEvent.setId(26L);
        groupEvent.setJobType(OutboxJobType.RECONCILE_GROUP);
        groupEvent.setAggregateType(OutboxAggregateType.GROUP);
        groupEvent.setAggregateId(88L);
        groupEvent.setLeaseToken(leaseGroup);

        UUID leaseMalformedEmail = UUID.randomUUID();
        KeycloakOutboxEventEntity malformedEmail = emailEvent(27L, 606L, leaseMalformedEmail);
        malformedEmail.setPayload(null);

        UUID leaseStaleEmail = UUID.randomUUID();
        KeycloakOutboxEventEntity staleEmail = emailEvent(28L, 607L, leaseStaleEmail);
        staleEmail.setPayload(Map.of("actions", List.of("  "), "lifespanSeconds", -5));

        when(userRepository.findById(606L)).thenReturn(Optional.of(activeLocalUser(606L, 1L, "kc-606")));
        when(userRepository.findById(607L)).thenReturn(Optional.of(activeLocalUser(607L, 1L, "kc-607")));
        when(userRepository.findById(608L)).thenReturn(Optional.empty());
        when(userRepository.findById(609L)).thenReturn(Optional.of(activeLocalUser(609L, 1L, "   ")));

        UUID leaseMissingEmail = UUID.randomUUID();
        KeycloakOutboxEventEntity missingUserEmail = emailEvent(29L, 608L, leaseMissingEmail);
        UUID leaseBlankSubEmail = UUID.randomUUID();
        KeycloakOutboxEventEntity blankSubEmail = emailEvent(30L, 609L, leaseBlankSubEmail);
        UUID leaseBadActionsType = UUID.randomUUID();
        KeycloakOutboxEventEntity badActionsType = emailEvent(31L, 606L, leaseBadActionsType);
        badActionsType.setPayload(Map.of("actions", "not-a-list", "lifespanSeconds", "invalid"));
        UUID leaseMixedActions = UUID.randomUUID();
        KeycloakOutboxEventEntity mixedActions = emailEvent(32L, 606L, leaseMixedActions);
        mixedActions.setPayload(Map.of("actions", java.util.Arrays.asList(null, "VERIFY_EMAIL"), "lifespanSeconds", 900));

        when(outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-unit"))
                .thenReturn(List.of(
                        new ClaimedOutboxJob(groupEvent, false),
                        new ClaimedOutboxJob(malformedEmail, false),
                        new ClaimedOutboxJob(staleEmail, false),
                        new ClaimedOutboxJob(missingUserEmail, false),
                        new ClaimedOutboxJob(blankSubEmail, false),
                        new ClaimedOutboxJob(badActionsType, false),
                        new ClaimedOutboxJob(mixedActions, false)));
        when(outboxStore.markActionEmailDispatchedWithCas(27L, leaseMalformedEmail)).thenReturn(true);
        when(outboxStore.markActionEmailDispatchedWithCas(28L, leaseStaleEmail)).thenReturn(false);
        when(outboxStore.markActionEmailDispatchedWithCas(31L, leaseBadActionsType)).thenReturn(true);
        when(outboxStore.markActionEmailDispatchedWithCas(32L, leaseMixedActions)).thenReturn(true);

        TenantContext.setTenantId("beta");
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(7);
        assertThat(TenantContext.getTenantId()).isEqualTo("beta");
        verify(outboxStore).markRetryOrFailedWithCas(
                eq(26L), eq(leaseGroup), eq(88L), eq(OutboxAggregateType.GROUP),
                eq(OutboxJobType.RECONCILE_GROUP), anyString());
        verify(outboxStore).cancelActionEmailWithCas(eq(29L), eq(leaseMissingEmail), anyString());
        verify(outboxStore).deferActionEmailWithCas(eq(30L), eq(leaseBlankSubEmail), any(Duration.class));
        verify(keycloakUserSyncClient, times(2))
                .sendActionEmail("acme", "kc-606", List.of("UPDATE_PASSWORD"), 3600);
        verify(keycloakUserSyncClient).sendActionEmail("acme", "kc-606", List.of("VERIFY_EMAIL"), 900);
        verify(keycloakUserSyncClient, never()).sendActionEmail(eq("acme"), eq("kc-607"), any(), anyInt());

        // MAX_FOLLOW_UP_PASSES cap + casUpdated == false branch
        UUID leaseCap = UUID.randomUUID();
        KeycloakOutboxEventEntity dirtyLoopEvent = reconcileEvent(33L, 606L, leaseCap);
        when(outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-unit"))
                .thenReturn(List.of(new ClaimedOutboxJob(dirtyLoopEvent, false)));
        when(keycloakUserSyncClient.reconcileUser(eq("acme"), any(UserReconcilePayload.class)))
                .thenReturn(new ReconcileResult("kc-606", false));
        when(outboxStore.completeReconcileWithCas(
                33L, leaseCap, 606L, OutboxAggregateType.USER, 1L, "kc-606", false, false))
                .thenReturn(new CasCompletionResult(true, true))
                .thenReturn(new CasCompletionResult(true, true))
                .thenReturn(new CasCompletionResult(true, true))
                .thenReturn(new CasCompletionResult(false, true));
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(3);
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(1);
    }

    @Test
    void outboxServiceShouldHandleNonTxBoundedDispatchFailureIsolationAndShutdown() {
        when(outboxStore.claimNextJobs(anyInt(), any(Duration.class), anyString()))
                .thenReturn(List.of(new ClaimedOutboxJob(reconcileEvent(40L, 501L, UUID.randomUUID()), false)))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("Transient error"));
        when(userRepository.findById(501L)).thenReturn(Optional.empty());

        TenantContext.setTenantId("beta");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        outboxService.dispatchPendingBatch("acme", 5);
        assertThat(TenantContext.getTenantId()).isEqualTo("beta");

        // Exception inside bounded dispatch is caught and restores TenantContext
        outboxService.dispatchPendingBatch("acme", 1);
        assertThat(TenantContext.getTenantId()).isEqualTo("beta");

        // Multi-iteration loop + active TX without synchronization active
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(outboxStore.claimNextJobs(anyInt(), any(Duration.class), anyString())).thenReturn(List.of());
        outboxService.dispatchPendingBatch("acme", 2);

        when(outboxStore.claimNextJobs(anyInt(), any(Duration.class), anyString()))
                .thenReturn(List.of(new ClaimedOutboxJob(reconcileEvent(41L, 501L, UUID.randomUUID()), false)))
                .thenReturn(List.of(new ClaimedOutboxJob(reconcileEvent(42L, 501L, UUID.randomUUID()), false)));
        TransactionSynchronizationManager.setActualTransactionActive(false);
        outboxService.dispatchPendingBatch("acme", 2);

        OutboxService ownedExecutorService = new OutboxService(outboxStore, worker, outboxProperties);
        ownedExecutorService.shutdown();
        outboxService.shutdown();
    }

    @Test
    void outboxServiceShouldDeferDispatchUntilAfterCommitWhenTransactionActive() {
        outboxService.enqueueUserReconcile(701L, 1L);
        verify(outboxStore).upsertUserReconcile(701L, 1L);

        outboxService.enqueueActionEmail(701L, List.of("UPDATE_PASSWORD"), 3600);
        verify(outboxStore).enqueueActionEmail(701L, List.of("UPDATE_PASSWORD"), 3600);

        when(outboxStore.claimNextJobs(anyInt(), any(Duration.class), anyString())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        outboxService.dispatchPendingBatch("acme", 2);
        // Must NOT claim or call Keycloak before TX commits!
        verify(outboxStore, never()).claimNextJobs(anyInt(), any(Duration.class), anyString());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(outboxStore).claimNextJobs(20, Duration.ofSeconds(60), "worker-unit");
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    private static TenantEntity tenant(String tenantId) {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId(tenantId);
        entity.setStatus(TenantStatus.ACTIVE);
        return entity;
    }

    private static KeycloakOutboxEventEntity reconcileEvent(long id, long userId, UUID leaseToken) {
        KeycloakOutboxEventEntity event = new KeycloakOutboxEventEntity();
        event.setId(id);
        event.setJobType(OutboxJobType.RECONCILE_USER);
        event.setAggregateType(OutboxAggregateType.USER);
        event.setAggregateId(userId);
        event.setTargetVersion(1L);
        event.setStatus(OutboxStatus.IN_PROGRESS);
        event.setLeaseToken(leaseToken);
        return event;
    }

    private static KeycloakOutboxEventEntity emailEvent(long id, long userId, UUID leaseToken) {
        KeycloakOutboxEventEntity event = new KeycloakOutboxEventEntity();
        event.setId(id);
        event.setJobType(OutboxJobType.SEND_ACTION_EMAIL);
        event.setAggregateType(OutboxAggregateType.USER);
        event.setAggregateId(userId);
        event.setStatus(OutboxStatus.IN_PROGRESS);
        event.setLeaseToken(leaseToken);
        event.setPayload(Map.of("actions", List.of("UPDATE_PASSWORD"), "lifespanSeconds", 3600));
        return event;
    }

    private static UserEntity activeLocalUser(long id, long syncVersion, String keycloakSub) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        user.setUserName("user_" + id);
        user.setEmailId("user_" + id + "@" + "acme.invalid");
        user.setFirstName("First");
        user.setLastName("Last");
        user.setLocale("en");
        user.setStatus(UserStatus.ACTIVE);
        user.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
        user.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        user.setSyncVersion(syncVersion);
        user.setSyncedVersion(0L);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        return user;
    }
}
