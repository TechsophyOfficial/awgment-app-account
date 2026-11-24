package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Facade for enqueuing coalesced reconciliation / discrete action-email jobs, claiming
 * aggregate-serialized leases for sync-first revocation, and triggering bounded,
 * per-tenant-coalesced post-commit outbox dispatch.
 */
@Slf4j
@Service
public class OutboxService {

    private static final int MAX_DISPATCH_ITERATIONS = 10;
    private static final int DISPATCH_THREADS = 4;
    private static final int DISPATCH_QUEUE_CAPACITY = 128;

    private final OutboxStore outboxStore;
    private final CoalescedOutboxWorker outboxWorker;
    private final OutboxProperties outboxProperties;
    private final TenantLookupService tenantLookupService;
    private final Executor dispatchExecutor;
    private final ExecutorService ownedExecutor;
    private final Set<String> queuedDispatchTenants = ConcurrentHashMap.newKeySet();

    @Autowired
    public OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            ObjectProvider<TenantLookupService> tenantLookupServiceProvider) {
        this(
                outboxStore,
                outboxWorker,
                outboxProperties,
                tenantLookupServiceProvider != null ? tenantLookupServiceProvider.getIfAvailable() : null,
                createDefaultDispatchExecutor()
        );
    }

    OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties) {
        this(outboxStore, outboxWorker, outboxProperties, null, createDefaultDispatchExecutor());
    }

    OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            Executor dispatchExecutor) {
        this(outboxStore, outboxWorker, outboxProperties, null, dispatchExecutor, false);
    }

    OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            TenantLookupService tenantLookupService,
            Executor dispatchExecutor) {
        this(outboxStore, outboxWorker, outboxProperties, tenantLookupService, dispatchExecutor, false);
    }

    private OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            TenantLookupService tenantLookupService,
            ExecutorService ownedExecutor) {
        this(outboxStore, outboxWorker, outboxProperties, tenantLookupService, ownedExecutor, true);
    }

    private OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            TenantLookupService tenantLookupService,
            Executor dispatchExecutor,
            boolean ownsExecutor) {
        this.outboxStore = outboxStore;
        this.outboxWorker = outboxWorker;
        this.outboxProperties = outboxProperties;
        this.tenantLookupService = tenantLookupService;
        this.dispatchExecutor = dispatchExecutor;
        this.ownedExecutor = ownsExecutor && dispatchExecutor instanceof ExecutorService es ? es : null;
    }

    public void enqueueUserReconcile(long userId, long targetVersion) {
        outboxStore.upsertUserReconcile(userId, targetVersion);
    }

    public void enqueueActionEmail(long userId, List<String> actions, int lifespanSeconds) {
        outboxStore.enqueueActionEmail(userId, actions, lifespanSeconds);
    }

    public Optional<ClaimedOutboxJob> claimUserReconcileLease(long userId) {
        Duration leaseTtl = Duration.ofSeconds(outboxProperties.getLeaseTtlSeconds());
        return outboxStore.claimSpecificUserReconcileJob(userId, leaseTtl, outboxProperties.getWorkerId());
    }

    public CasCompletionResult completeUserRevocationSync(
            long outboxId,
            UUID leaseToken,
            long userId,
            long reconciledVersion,
            String keycloakSub,
            boolean wasReclaimed) {
        return outboxStore.completeReconcileWithCas(
                outboxId,
                leaseToken,
                userId,
                OutboxAggregateType.USER,
                reconciledVersion,
                keycloakSub,
                wasReclaimed,
                false
        );
    }

    public boolean deferUserRevocationSync(
            long outboxId,
            UUID leaseToken,
            long userId,
            String errorMessage,
            boolean schedulePostRecoveryVerification) {
        return outboxStore.markRetryOrFailedWithCas(
                outboxId,
                leaseToken,
                userId,
                OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER,
                errorMessage,
                schedulePostRecoveryVerification
        );
    }

    public boolean releaseUserReconcileToPending(long outboxId, UUID leaseToken) {
        return outboxStore.releaseReconcileToPendingWithCas(outboxId, leaseToken);
    }

    public void dispatchPendingBatch(String tenantId, int maxIterations) {
        TenantIdValidator.validate(tenantId);
        int boundedIterations = Math.max(1, Math.min(maxIterations, MAX_DISPATCH_ITERATIONS));
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (!queuedDispatchTenants.add(tenantId)) {
                        return;
                    }
                    try {
                        dispatchExecutor.execute(() -> runBoundedDispatch(tenantId, boundedIterations));
                    } catch (RuntimeException ex) {
                        queuedDispatchTenants.remove(tenantId);
                    }
                }
            });
            return;
        }
        runBoundedDispatch(tenantId, boundedIterations);
    }

    private void runBoundedDispatch(String tenantId, int boundedIterations) {
        queuedDispatchTenants.remove(tenantId);
        if (tenantLookupService != null
                && tenantLookupService.findStatus(tenantId).orElse(null) != TenantStatus.ACTIVE) {
            return;
        }
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            int batchSize = outboxProperties.getBatchSize();
            for (int i = 0; i < boundedIterations; i++) {
                int processed = outboxWorker.processTenantQueue(tenantId, batchSize);
                if (processed <= 0) {
                    break;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Bounded outbox dispatch failed for tenant '{}': {}",
                    ProblemDetailFactory.sanitizeForLog(tenantId),
                    ProblemDetailFactory.sanitizeForLog(ex.getMessage()));
        } finally {
            if (TenantContext.DEFAULT_TENANT.equals(previousTenant)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
            try {
                if (!ownedExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    ownedExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                ownedExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ExecutorService createDefaultDispatchExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                DISPATCH_THREADS,
                DISPATCH_THREADS,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(DISPATCH_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "awgment-outbox-dispatch-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }
}
