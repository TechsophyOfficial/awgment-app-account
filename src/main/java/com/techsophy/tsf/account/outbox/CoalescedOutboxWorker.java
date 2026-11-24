package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.keycloak.KeycloakUserSyncClient;
import com.techsophy.tsf.account.keycloak.ReconcileResult;
import com.techsophy.tsf.account.keycloak.UserReconcilePayload;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantEntity;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import com.techsophy.tsf.account.tenant.TenantRepository;
import com.techsophy.tsf.account.tenant.TenantStatus;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserRepository;
import com.techsophy.tsf.account.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Scheduled and on-demand worker draining coalesced {@code tp_keycloak_outbox} queues per active tenant.
 * Claims bounded batches inside short DB transactions and performs all Keycloak Admin REST calls
 * completely outside database transactions.
 */
@Slf4j
@Component
public class CoalescedOutboxWorker {

    private static final int MAX_FOLLOW_UP_PASSES = 3;
    private static final int DEFAULT_ACTION_EMAIL_LIFESPAN_SECONDS = 3600;

    private final OutboxStore outboxStore;
    private final UserRepository userRepository;
    private final KeycloakUserSyncClient keycloakUserSyncClient;
    private final TenantRepository tenantRepository;
    private final OutboxProperties outboxProperties;

    public CoalescedOutboxWorker(
            OutboxStore outboxStore,
            UserRepository userRepository,
            KeycloakUserSyncClient keycloakUserSyncClient,
            TenantRepository tenantRepository,
            OutboxProperties outboxProperties) {
        this.outboxStore = outboxStore;
        this.userRepository = userRepository;
        this.keycloakUserSyncClient = keycloakUserSyncClient;
        this.tenantRepository = tenantRepository;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(fixedDelayString = "${awgment.outbox.poll-interval-ms:2000}")
    public void pollAllActiveTenants() {
        if (!outboxProperties.isWorkerEnabled()) {
            return;
        }
        TenantContext.clear();
        try {
            List<TenantEntity> activeTenants = tenantRepository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE);
            for (TenantEntity tenant : activeTenants) {
                String tenantId = tenant.getTenantId();
                try {
                    processTenantQueue(tenantId, outboxProperties.getBatchSize());
                } catch (RuntimeException ex) {
                    log.warn("Outbox poll failed for tenant '{}': {}", tenantId, ex.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    public int processTenantQueue(String tenantId, int batchSize) {
        TenantIdValidator.validate(tenantId);
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        int totalProcessed = 0;
        try {
            Duration leaseTtl = Duration.ofSeconds(outboxProperties.getLeaseTtlSeconds());
            String workerId = outboxProperties.getWorkerId();
            int pass = 0;
            boolean followUpNeeded;
            do {
                followUpNeeded = false;
                List<ClaimedOutboxJob> claimed = outboxStore.claimNextJobs(batchSize, leaseTtl, workerId);
                if (claimed.isEmpty()) {
                    break;
                }
                for (ClaimedOutboxJob job : claimed) {
                    totalProcessed++;
                    boolean dirtyFollowUp = processClaimedJob(tenantId, job);
                    if (dirtyFollowUp) {
                        followUpNeeded = true;
                    }
                }
                pass++;
            } while (followUpNeeded && pass < MAX_FOLLOW_UP_PASSES);
            return totalProcessed;
        } finally {
            if (TenantContext.DEFAULT_TENANT.equals(previousTenant)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }

    private boolean processClaimedJob(String tenantId, ClaimedOutboxJob claimed) {
        KeycloakOutboxEventEntity event = claimed.event();
        return switch (event.getJobType()) {
            case RECONCILE_USER -> processReconcileUser(tenantId, claimed);
            case SEND_ACTION_EMAIL -> {
                processSendActionEmail(tenantId, event);
                yield false;
            }
            case RECONCILE_GROUP -> {
                outboxStore.markRetryOrFailedWithCas(
                        event.getId(),
                        event.getLeaseToken(),
                        event.getAggregateId(),
                        event.getAggregateType(),
                        event.getJobType(),
                        "RECONCILE_GROUP handler is not yet enabled."
                );
                yield false;
            }
        };
    }

    private boolean processReconcileUser(String tenantId, ClaimedOutboxJob claimed) {
        KeycloakOutboxEventEntity event = claimed.event();
        UserEntity user = userRepository.findById(event.getAggregateId()).orElse(null);
        if (user == null) {
            outboxStore.markRetryOrFailedWithCas(
                    event.getId(),
                    event.getLeaseToken(),
                    event.getAggregateId(),
                    event.getAggregateType(),
                    event.getJobType(),
                    "Target user record not found for reconciliation."
            );
            return false;
        }

        UserReconcilePayload payload = UserReconcilePayload.from(user);
        try {
            ReconcileResult result = keycloakUserSyncClient.reconcileUser(tenantId, payload);
            CasCompletionResult completion = outboxStore.completeReconcileWithCas(
                    event.getId(),
                    event.getLeaseToken(),
                    user.getId(),
                    OutboxAggregateType.USER,
                    payload.syncVersion(),
                    result.keycloakSub(),
                    claimed.wasReclaimed(),
                    result.fenced()
            );
            return completion.casUpdated() && completion.reQueuedBecauseDirty();
        } catch (RuntimeException ex) {
            outboxStore.markRetryOrFailedWithCas(
                    event.getId(),
                    event.getLeaseToken(),
                    event.getAggregateId(),
                    event.getAggregateType(),
                    event.getJobType(),
                    ex.getMessage()
            );
            return false;
        }
    }

    private void processSendActionEmail(String tenantId, KeycloakOutboxEventEntity event) {
        UserEntity user = userRepository.findById(event.getAggregateId()).orElse(null);
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || user.getIdentitySource() != IdentitySource.KEYCLOAK_LOCAL) {
            outboxStore.cancelActionEmailWithCas(
                    event.getId(),
                    event.getLeaseToken(),
                    "User is not an ACTIVE KEYCLOAK_LOCAL account."
            );
            return;
        }
        if (user.getKeycloakSub() == null || user.getKeycloakSub().isBlank()) {
            Duration deferDelay = Duration.ofMillis(Math.max(500L, outboxProperties.getPollIntervalMs()));
            outboxStore.deferActionEmailWithCas(event.getId(), event.getLeaseToken(), deferDelay);
            return;
        }

        boolean stampedDispatched = outboxStore.markActionEmailDispatchedWithCas(
                event.getId(), event.getLeaseToken());
        if (!stampedDispatched || !outboxProperties.isActionEmailDeliveryEnabled()) {
            return;
        }

        List<String> actions = extractActions(event.getPayload());
        int lifespanSeconds = extractLifespan(event.getPayload());
        try {
            keycloakUserSyncClient.sendActionEmail(tenantId, user.getKeycloakSub(), actions, lifespanSeconds);
        } catch (RuntimeException ex) {
            outboxStore.markActionEmailAmbiguousFailure(event.getId(), ex.getMessage());
        }
    }

    private static List<String> extractActions(Map<String, Object> payload) {
        if (payload == null) {
            return List.of("UPDATE_PASSWORD");
        }
        Object raw = payload.get("actions");
        if (!(raw instanceof Collection<?> collection)) {
            return List.of("UPDATE_PASSWORD");
        }
        List<String> actions = new ArrayList<>();
        for (Object item : collection) {
            if (item != null && !String.valueOf(item).isBlank()) {
                actions.add(String.valueOf(item).trim());
            }
        }
        return actions.isEmpty() ? List.of("UPDATE_PASSWORD") : List.copyOf(actions);
    }

    private static int extractLifespan(Map<String, Object> payload) {
        if (payload == null) {
            return DEFAULT_ACTION_EMAIL_LIFESPAN_SECONDS;
        }
        Object raw = payload.get("lifespanSeconds");
        if (raw instanceof Number num && num.intValue() > 0) {
            return num.intValue();
        }
        return DEFAULT_ACTION_EMAIL_LIFESPAN_SECONDS;
    }
}
