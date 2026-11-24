package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Facade for enqueuing coalesced reconciliation / discrete action-email jobs and triggering
 * post-commit or bounded synchronous outbox dispatch.
 */
@Slf4j
@Service
public class OutboxService {

    private static final int MAX_DISPATCH_ITERATIONS = 10;
    private static final int DISPATCH_THREADS = 4;

    private final OutboxStore outboxStore;
    private final CoalescedOutboxWorker outboxWorker;
    private final OutboxProperties outboxProperties;
    private final Executor dispatchExecutor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties) {
        this(outboxStore, outboxWorker, outboxProperties, createDefaultDispatchExecutor());
    }

    OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            Executor dispatchExecutor) {
        this.outboxStore = outboxStore;
        this.outboxWorker = outboxWorker;
        this.outboxProperties = outboxProperties;
        this.dispatchExecutor = dispatchExecutor;
        this.ownedExecutor = null;
    }

    private OutboxService(
            OutboxStore outboxStore,
            CoalescedOutboxWorker outboxWorker,
            OutboxProperties outboxProperties,
            ExecutorService ownedExecutor) {
        this.outboxStore = outboxStore;
        this.outboxWorker = outboxWorker;
        this.outboxProperties = outboxProperties;
        this.dispatchExecutor = ownedExecutor;
        this.ownedExecutor = ownedExecutor;
    }

    public void enqueueUserReconcile(long userId, long targetVersion) {
        outboxStore.upsertUserReconcile(userId, targetVersion);
    }

    public void enqueueActionEmail(long userId, List<String> actions, int lifespanSeconds) {
        outboxStore.enqueueActionEmail(userId, actions, lifespanSeconds);
    }

    public void dispatchPendingBatch(String tenantId, int maxIterations) {
        TenantIdValidator.validate(tenantId);
        int boundedIterations = Math.max(1, Math.min(maxIterations, MAX_DISPATCH_ITERATIONS));
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchExecutor.execute(() -> runBoundedDispatch(tenantId, boundedIterations));
                }
            });
            return;
        }
        runBoundedDispatch(tenantId, boundedIterations);
    }

    private void runBoundedDispatch(String tenantId, int boundedIterations) {
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
            ownedExecutor.shutdownNow();
        }
    }

    private static ExecutorService createDefaultDispatchExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newFixedThreadPool(DISPATCH_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "awgment-outbox-dispatch-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }
}
