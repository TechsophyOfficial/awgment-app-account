package com.techsophy.tsf.account.outbox;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistence store for the coalesced Keycloak outbox implementing PostgreSQL partial-index
 * coalesced upserts, {@code FOR UPDATE SKIP LOCKED} lease claims, atomic {@code lease_token} CAS
 * completions/retries with durable verification carryover, action-email gating transitions, and
 * Micrometer failure/SLA observability without holding DB locks across network I/O.
 */
@Slf4j
@Component
public class OutboxStore {

    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int MAX_CLAIM_BATCH = 200;
    private static final int MAX_WORKER_ID_LENGTH = 100;
    private static final long SLA_LAG_THRESHOLD_SECONDS = 30L;
    private static final String OUTBOX_SYNC_FAILED_METRIC = "awgment_outbox_sync_failed_total";
    private static final String OUTBOX_OLDEST_LAG_METRIC = "awgment_outbox_oldest_pending_age_seconds";
    private static final String OUTBOX_SLA_BREACH_METRIC = "awgment_outbox_sla_breach_total";

    private static final String PG_UPSERT_USER_RECONCILE_SQL = """
            INSERT INTO tp_keycloak_outbox (
                id, job_type, aggregate_type, aggregate_id,
                target_version, dirty, reclaimed_verification_pending, pending_since,
                status, retry_count, next_retry_at, created_on, updated_on
            ) VALUES (
                :id, 'RECONCILE_USER', 'USER', :aggregateId,
                :targetVersion, FALSE, FALSE, NOW(),
                'PENDING', 0, NOW(), NOW(), NOW()
            )
            ON CONFLICT (aggregate_type, aggregate_id)
            WHERE job_type IN ('RECONCILE_USER', 'RECONCILE_GROUP')
            DO UPDATE SET
                target_version = GREATEST(COALESCE(tp_keycloak_outbox.target_version, 0), EXCLUDED.target_version),
                dirty = CASE WHEN tp_keycloak_outbox.status = 'IN_PROGRESS' THEN TRUE ELSE FALSE END,
                reclaimed_verification_pending = CASE
                    WHEN tp_keycloak_outbox.status = 'IN_PROGRESS' THEN tp_keycloak_outbox.reclaimed_verification_pending
                    WHEN tp_keycloak_outbox.verification_required_at IS NOT NULL THEN TRUE
                    ELSE tp_keycloak_outbox.reclaimed_verification_pending
                END,
                pending_since = CASE
                    WHEN tp_keycloak_outbox.status IN ('PENDING', 'IN_PROGRESS')
                        THEN COALESCE(tp_keycloak_outbox.pending_since, NOW())
                    ELSE NOW()
                END,
                status = CASE WHEN tp_keycloak_outbox.status = 'IN_PROGRESS' THEN 'IN_PROGRESS' ELSE 'PENDING' END,
                verification_required_at = NULL,
                retry_count = CASE WHEN tp_keycloak_outbox.status = 'IN_PROGRESS' THEN tp_keycloak_outbox.retry_count ELSE 0 END,
                next_retry_at = CASE WHEN tp_keycloak_outbox.status = 'IN_PROGRESS' THEN tp_keycloak_outbox.next_retry_at ELSE NOW() END,
                last_error = NULL,
                updated_on = NOW()
            """;

    private static final String PG_CLAIM_SELECT_SQL = """
            SELECT id, status
            FROM tp_keycloak_outbox
            WHERE (status = 'PENDING' AND next_retry_at <= NOW())
               OR (status = 'COMPLETED' AND verification_required_at IS NOT NULL AND verification_required_at <= NOW())
               OR (status = 'IN_PROGRESS' AND locked_at < NOW() - MAKE_INTERVAL(secs => :leaseTtlSeconds))
            ORDER BY CASE WHEN job_type IN ('RECONCILE_USER', 'RECONCILE_GROUP') THEN 0 ELSE 1 END,
                     next_retry_at ASC,
                     created_on ASC,
                     id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    private static final String PG_CLAIM_SPECIFIC_USER_SELECT_SQL = """
            SELECT id, status
            FROM tp_keycloak_outbox
            WHERE aggregate_type = 'USER'
              AND aggregate_id = :aggregateId
              AND job_type = 'RECONCILE_USER'
              AND (
                    status = 'PENDING'
                 OR (status = 'COMPLETED' AND verification_required_at IS NOT NULL)
                 OR (status = 'IN_PROGRESS' AND locked_at < NOW() - MAKE_INTERVAL(secs => :leaseTtlSeconds))
              )
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """;

    private static final String PG_CLAIM_UPDATE_SQL = """
            UPDATE tp_keycloak_outbox
            SET status = 'IN_PROGRESS',
                dirty = FALSE,
                lease_token = :leaseToken,
                locked_at = NOW(),
                locked_by = :workerId,
                verification_required_at = NULL,
                reclaimed_verification_pending = (reclaimed_verification_pending OR :wasReclaimed),
                updated_on = NOW()
            WHERE id = :id
            """;

    private static final String PG_LOCK_BY_LEASE_SQL = """
            SELECT dirty, target_version, retry_count, reclaimed_verification_pending, pending_since
            FROM tp_keycloak_outbox
            WHERE id = :id
              AND lease_token = :leaseToken
              AND status = 'IN_PROGRESS'
            FOR UPDATE
            """;

    private static final String PG_COMPLETE_CAS_SQL = """
            UPDATE tp_keycloak_outbox
            SET status = :nextStatus,
                retry_count = 0,
                verification_required_at = CASE
                    WHEN :scheduleVerification = TRUE THEN NOW() + MAKE_INTERVAL(secs => :verificationDelaySeconds)
                    ELSE NULL
                END,
                reclaimed_verification_pending = CASE
                    WHEN :reQueueDirty = TRUE THEN :carryReclaimedVerification
                    ELSE FALSE
                END,
                pending_since = CASE
                    WHEN :reQueueDirty = TRUE THEN COALESCE(pending_since, NOW())
                    ELSE NULL
                END,
                dirty = FALSE,
                lease_token = NULL,
                locked_at = NULL,
                locked_by = NULL,
                last_error = NULL,
                next_retry_at = CASE WHEN :reQueueDirty = TRUE THEN NOW() ELSE next_retry_at END,
                updated_on = NOW()
            WHERE id = :id
              AND lease_token = :leaseToken
              AND status = 'IN_PROGRESS'
            """;

    private static final String PG_DIRTY_REQUEUE_ON_FAIL_CAS_SQL = """
            UPDATE tp_keycloak_outbox
            SET status = 'PENDING',
                dirty = FALSE,
                retry_count = 0,
                next_retry_at = NOW(),
                last_error = NULL,
                reclaimed_verification_pending = (reclaimed_verification_pending OR :scheduleVerification),
                pending_since = COALESCE(pending_since, NOW()),
                lease_token = NULL,
                locked_at = NULL,
                locked_by = NULL,
                updated_on = NOW()
            WHERE id = :id
              AND lease_token = :leaseToken
              AND status = 'IN_PROGRESS'
            """;

    private static final String PG_FAIL_CAS_SQL = """
            UPDATE tp_keycloak_outbox
            SET status = :nextStatus,
                retry_count = :retryCount,
                next_retry_at = CASE
                    WHEN :exhausted = TRUE THEN NOW()
                    ELSE NOW() + MAKE_INTERVAL(secs => :delaySeconds)
                END,
                last_error = :lastError,
                reclaimed_verification_pending = (reclaimed_verification_pending OR :scheduleVerification),
                lease_token = NULL,
                locked_at = NULL,
                locked_by = NULL,
                updated_on = NOW()
            WHERE id = :id
              AND lease_token = :leaseToken
              AND status = 'IN_PROGRESS'
            """;

    private static final String PG_RELEASE_TO_PENDING_CAS_SQL = """
            UPDATE tp_keycloak_outbox
            SET status = 'PENDING',
                dirty = FALSE,
                retry_count = 0,
                next_retry_at = NOW(),
                last_error = NULL,
                pending_since = COALESCE(pending_since, NOW()),
                lease_token = NULL,
                locked_at = NULL,
                locked_by = NULL,
                updated_on = NOW()
            WHERE id = :id
              AND lease_token = :leaseToken
              AND status = 'IN_PROGRESS'
            """;

    private static final String PG_RESET_FAILED_CAS_SQL = """
            UPDATE tp_keycloak_outbox
            SET status = 'PENDING',
                dirty = FALSE,
                retry_count = 0,
                next_retry_at = NOW(),
                last_error = NULL,
                lease_token = NULL,
                locked_at = NULL,
                locked_by = NULL,
                verification_required_at = NULL,
                reclaimed_verification_pending = FALSE,
                pending_since = NOW(),
                updated_on = NOW()
            WHERE id = :id
              AND status = 'FAILED'
              AND NOT (job_type = 'SEND_ACTION_EMAIL' AND ambiguous_delivery = TRUE)
            """;

    private final EntityManager entityManager;
    private final KeycloakOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final IdGeneratorImpl idGenerator;
    private final OutboxProperties outboxProperties;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> tenantLagSeconds = new ConcurrentHashMap<>();
    private volatile Boolean postgresDatabase;

    @Autowired
    public OutboxStore(
            EntityManager entityManager,
            KeycloakOutboxRepository outboxRepository,
            UserRepository userRepository,
            IdGeneratorImpl idGenerator,
            OutboxProperties outboxProperties,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(entityManager, outboxRepository, userRepository, idGenerator, outboxProperties,
                meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null);
    }

    OutboxStore(
            EntityManager entityManager,
            KeycloakOutboxRepository outboxRepository,
            UserRepository userRepository,
            IdGeneratorImpl idGenerator,
            OutboxProperties outboxProperties) {
        this(entityManager, outboxRepository, userRepository, idGenerator, outboxProperties, (MeterRegistry) null);
    }

    OutboxStore(
            EntityManager entityManager,
            KeycloakOutboxRepository outboxRepository,
            UserRepository userRepository,
            IdGeneratorImpl idGenerator,
            OutboxProperties outboxProperties,
            MeterRegistry meterRegistry) {
        this.entityManager = entityManager;
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.outboxProperties = outboxProperties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void upsertUserReconcile(long userId, long targetVersion) {
        entityManager.flush();
        if (isPostgreSql()) {
            entityManager.createNativeQuery(PG_UPSERT_USER_RECONCILE_SQL)
                    .setParameter("id", idGenerator.nextId().longValue())
                    .setParameter("aggregateId", userId)
                    .setParameter("targetVersion", targetVersion)
                    .executeUpdate();
            return;
        }
        upsertUserReconcilePortable(userId, targetVersion);
    }

    @Transactional
    public long enqueueActionEmail(long userId, List<String> actions, int lifespanSeconds) {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Action list must not be empty.");
        }
        List<String> cleanActions = actions.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(String::trim)
                .toList();
        if (cleanActions.isEmpty()) {
            throw new IllegalArgumentException("Action list must contain at least one non-blank action.");
        }
        if (lifespanSeconds <= 0) {
            throw new IllegalArgumentException("lifespanSeconds must be > 0.");
        }

        Instant now = Instant.now();
        KeycloakOutboxEventEntity entity = new KeycloakOutboxEventEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setJobType(OutboxJobType.SEND_ACTION_EMAIL);
        entity.setAggregateType(OutboxAggregateType.USER);
        entity.setAggregateId(userId);
        entity.setDirty(false);
        entity.setReclaimedVerificationPending(false);
        entity.setAmbiguousDelivery(false);
        entity.setStatus(OutboxStatus.PENDING);
        entity.setPayload(Map.of(
                "actions", cleanActions,
                "lifespanSeconds", lifespanSeconds
        ));
        entity.setRetryCount(0);
        entity.setNextRetryAt(now);
        entity.setCreatedOn(now);
        entity.setUpdatedOn(now);
        KeycloakOutboxEventEntity saved = outboxRepository.saveAndFlush(entity);
        if (isPostgreSql()) {
            entityManager.createNativeQuery(
                    "UPDATE tp_keycloak_outbox SET next_retry_at = NOW(), created_on = NOW(), updated_on = NOW() WHERE id = :id")
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            entityManager.refresh(saved);
        }
        return saved.getId();
    }

    @Transactional
    public List<ClaimedOutboxJob> claimNextJobs(int limit, Duration leaseTtl, String workerId) {
        if (limit <= 0) {
            return List.of();
        }
        entityManager.flush();
        int effectiveLimit = Math.min(limit, MAX_CLAIM_BATCH);
        Duration effectiveLease = effectiveLeaseDuration(leaseTtl);
        String effectiveWorker = normalizeWorkerId(workerId);

        if (isPostgreSql()) {
            return claimNextJobsPostgres(effectiveLimit, effectiveLease, effectiveWorker);
        }
        return claimNextJobsPortable(effectiveLimit, effectiveLease, effectiveWorker);
    }

    @Transactional
    public Optional<ClaimedOutboxJob> claimSpecificUserReconcileJob(
            long userId, Duration leaseTtl, String workerId) {
        entityManager.flush();
        Duration effectiveLease = effectiveLeaseDuration(leaseTtl);
        String effectiveWorker = normalizeWorkerId(workerId);

        if (isPostgreSql()) {
            @SuppressWarnings("unchecked")
            List<Object[]> candidates = entityManager.createNativeQuery(PG_CLAIM_SPECIFIC_USER_SELECT_SQL)
                    .setParameter("aggregateId", userId)
                    .setParameter("leaseTtlSeconds", (int) Math.max(1L, effectiveLease.toSeconds()))
                    .getResultList();
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            Object[] candidate = candidates.getFirst();
            long id = ((Number) candidate[0]).longValue();
            boolean wasReclaimed = OutboxStatus.IN_PROGRESS.name().equals(String.valueOf(candidate[1]));
            UUID leaseToken = UUID.randomUUID();
            entityManager.createNativeQuery(PG_CLAIM_UPDATE_SQL)
                    .setParameter("leaseToken", leaseToken)
                    .setParameter("workerId", effectiveWorker)
                    .setParameter("wasReclaimed", wasReclaimed)
                    .setParameter("id", id)
                    .executeUpdate();
            return outboxRepository.findById(id)
                    .map(entity -> new ClaimedOutboxJob(entity, wasReclaimed));
        }

        Instant now = Instant.now();
        Instant expiredCutoff = now.minus(effectiveLease);
        List<KeycloakOutboxEventEntity> candidates = entityManager.createQuery("""
                SELECT e FROM KeycloakOutboxEventEntity e
                WHERE e.aggregateType = com.techsophy.tsf.account.outbox.OutboxAggregateType.USER
                  AND e.aggregateId = :aggregateId
                  AND e.jobType = com.techsophy.tsf.account.outbox.OutboxJobType.RECONCILE_USER
                  AND (e.status = com.techsophy.tsf.account.outbox.OutboxStatus.PENDING
                       OR (e.status = com.techsophy.tsf.account.outbox.OutboxStatus.COMPLETED
                           AND e.verificationRequiredAt IS NOT NULL)
                       OR (e.status = com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                           AND e.lockedAt < :expiredCutoff))
                """, KeycloakOutboxEventEntity.class)
                .setParameter("aggregateId", userId)
                .setParameter("expiredCutoff", expiredCutoff)
                .setMaxResults(1)
                .getResultList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        KeycloakOutboxEventEntity entity = candidates.getFirst();
        boolean wasReclaimed = entity.getStatus() == OutboxStatus.IN_PROGRESS;
        entity.setStatus(OutboxStatus.IN_PROGRESS);
        entity.setDirty(false);
        entity.setLeaseToken(UUID.randomUUID());
        entity.setLockedAt(now);
        entity.setLockedBy(effectiveWorker);
        entity.setVerificationRequiredAt(null);
        entity.setReclaimedVerificationPending(entity.isReclaimedVerificationPending() || wasReclaimed);
        entity.setUpdatedOn(now);
        KeycloakOutboxEventEntity saved = outboxRepository.saveAndFlush(entity);
        return Optional.of(new ClaimedOutboxJob(saved, wasReclaimed));
    }

    @Transactional
    public boolean releaseReconcileToPendingWithCas(long outboxId, UUID leaseToken) {
        if (leaseToken == null) {
            return false;
        }
        entityManager.flush();
        if (isPostgreSql()) {
            int updated = entityManager.createNativeQuery(PG_RELEASE_TO_PENDING_CAS_SQL)
                    .setParameter("id", outboxId)
                    .setParameter("leaseToken", leaseToken)
                    .executeUpdate();
            return updated == 1;
        }
        Instant now = Instant.now();
        return entityManager.createQuery("""
                UPDATE KeycloakOutboxEventEntity e
                SET e.status = com.techsophy.tsf.account.outbox.OutboxStatus.PENDING,
                    e.dirty = false,
                    e.retryCount = 0,
                    e.nextRetryAt = :now,
                    e.lastError = null,
                    e.leaseToken = null,
                    e.lockedAt = null,
                    e.lockedBy = null,
                    e.updatedOn = :now
                WHERE e.id = :id
                  AND e.leaseToken = :leaseToken
                  AND e.status = com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                """)
                .setParameter("now", now)
                .setParameter("id", outboxId)
                .setParameter("leaseToken", leaseToken)
                .executeUpdate() == 1;
    }

    @Transactional
    public boolean completeWithCas(
            long outboxId,
            UUID leaseToken,
            long aggregateId,
            long reconciledVersion,
            String keycloakSub,
            boolean wasReclaimed) {
        return completeReconcileWithCas(
                outboxId,
                leaseToken,
                aggregateId,
                OutboxAggregateType.USER,
                reconciledVersion,
                keycloakSub,
                wasReclaimed,
                false
        ).casUpdated();
    }

    @Transactional
    public CasCompletionResult completeReconcileWithCas(
            long outboxId,
            UUID leaseToken,
            long aggregateId,
            OutboxAggregateType aggregateType,
            long reconciledVersion,
            String keycloakSub,
            boolean wasReclaimed,
            boolean fenced) {
        if (leaseToken == null) {
            return new CasCompletionResult(false, false);
        }
        if (fenced) {
            OutboxJobType jobType = aggregateType == OutboxAggregateType.GROUP
                    ? OutboxJobType.RECONCILE_GROUP
                    : OutboxJobType.RECONCILE_USER;
            boolean updated = markRetryOrFailedWithCas(
                    outboxId,
                    leaseToken,
                    aggregateId,
                    aggregateType,
                    jobType,
                    "Fenced by higher awgment_sync_version in Keycloak.",
                    false
            );
            return new CasCompletionResult(updated, false);
        }

        entityManager.flush();
        boolean reQueueDirty;
        if (isPostgreSql()) {
            @SuppressWarnings("unchecked")
            List<Object[]> locked = entityManager.createNativeQuery(PG_LOCK_BY_LEASE_SQL)
                    .setParameter("id", outboxId)
                    .setParameter("leaseToken", leaseToken)
                    .getResultList();
            if (locked.isEmpty()) {
                return new CasCompletionResult(false, false);
            }
            Object[] row = locked.getFirst();
            boolean rowDirty = Boolean.TRUE.equals(row[0]);
            Long targetVer = row[1] != null ? ((Number) row[1]).longValue() : null;
            boolean rowVerificationPending = row.length > 3 && Boolean.TRUE.equals(row[3]);
            boolean effectiveVerificationPending = rowVerificationPending || wasReclaimed;
            reQueueDirty = rowDirty || (targetVer != null && targetVer > reconciledVersion);
            boolean scheduleVerification = !reQueueDirty && effectiveVerificationPending;
            boolean carryReclaimedVerification = reQueueDirty && effectiveVerificationPending;
            String nextStatus = reQueueDirty ? OutboxStatus.PENDING.name() : OutboxStatus.COMPLETED.name();

            int updated = entityManager.createNativeQuery(PG_COMPLETE_CAS_SQL)
                    .setParameter("nextStatus", nextStatus)
                    .setParameter("scheduleVerification", scheduleVerification)
                    .setParameter("verificationDelaySeconds", outboxProperties.getVerificationDelaySeconds())
                    .setParameter("reQueueDirty", reQueueDirty)
                    .setParameter("carryReclaimedVerification", carryReclaimedVerification)
                    .setParameter("id", outboxId)
                    .setParameter("leaseToken", leaseToken)
                    .executeUpdate();
            if (updated == 0) {
                return new CasCompletionResult(false, false);
            }
        } else {
            Optional<KeycloakOutboxEventEntity> opt = outboxRepository.findById(outboxId);
            if (opt.isEmpty()) {
                return new CasCompletionResult(false, false);
            }
            KeycloakOutboxEventEntity entity = opt.get();
            if (entity.getStatus() != OutboxStatus.IN_PROGRESS || !leaseToken.equals(entity.getLeaseToken())) {
                return new CasCompletionResult(false, false);
            }
            Instant now = Instant.now();
            boolean effectiveVerificationPending = entity.isReclaimedVerificationPending() || wasReclaimed;
            reQueueDirty = entity.isDirty()
                    || (entity.getTargetVersion() != null && entity.getTargetVersion() > reconciledVersion);
            entity.setStatus(reQueueDirty ? OutboxStatus.PENDING : OutboxStatus.COMPLETED);
            entity.setRetryCount(0);
            entity.setVerificationRequiredAt(
                    (!reQueueDirty && effectiveVerificationPending)
                            ? now.plusSeconds(outboxProperties.getVerificationDelaySeconds())
                            : null
            );
            entity.setReclaimedVerificationPending(reQueueDirty && effectiveVerificationPending);
            entity.setPendingSince(reQueueDirty
                    ? (entity.getPendingSince() != null ? entity.getPendingSince() : now)
                    : null);
            entity.setDirty(false);
            entity.setLeaseToken(null);
            entity.setLockedAt(null);
            entity.setLockedBy(null);
            entity.setLastError(null);
            if (reQueueDirty) {
                entity.setNextRetryAt(now);
            }
            entity.setUpdatedOn(now);
            outboxRepository.saveAndFlush(entity);
        }

        if (aggregateType == OutboxAggregateType.USER) {
            userRepository.updateSyncProgress(
                    aggregateId,
                    normalizeNullable(keycloakSub),
                    reconciledVersion,
                    SyncStatus.SYNCED,
                    Instant.now()
            );
        }
        return new CasCompletionResult(true, reQueueDirty);
    }

    @Transactional
    public boolean markRetryOrFailedWithCas(
            long outboxId,
            UUID leaseToken,
            long aggregateId,
            OutboxAggregateType aggregateType,
            OutboxJobType jobType,
            String errorMessage) {
        return markRetryOrFailedWithCas(
                outboxId,
                leaseToken,
                aggregateId,
                aggregateType,
                jobType,
                errorMessage,
                false
        );
    }

    @Transactional
    public boolean markRetryOrFailedWithCas(
            long outboxId,
            UUID leaseToken,
            long aggregateId,
            OutboxAggregateType aggregateType,
            OutboxJobType jobType,
            String errorMessage,
            boolean schedulePostRecoveryVerification) {
        if (leaseToken == null) {
            return false;
        }
        entityManager.flush();
        String safeError = sanitizeAndTruncateError(errorMessage);
        Long targetVer;
        boolean exhausted = false;
        boolean reQueuedDirty;
        Instant pendingSince;

        if (isPostgreSql()) {
            @SuppressWarnings("unchecked")
            List<Object[]> locked = entityManager.createNativeQuery(PG_LOCK_BY_LEASE_SQL)
                    .setParameter("id", outboxId)
                    .setParameter("leaseToken", leaseToken)
                    .getResultList();
            if (locked.isEmpty()) {
                return false;
            }
            Object[] row = locked.getFirst();
            boolean rowDirty = Boolean.TRUE.equals(row[0]);
            targetVer = row[1] != null ? ((Number) row[1]).longValue() : null;
            int currentRetries = row[2] != null ? ((Number) row[2]).intValue() : 0;
            pendingSince = row.length > 4 ? toInstant(row[4]) : null;

            if (rowDirty) {
                reQueuedDirty = true;
                int updated = entityManager.createNativeQuery(PG_DIRTY_REQUEUE_ON_FAIL_CAS_SQL)
                        .setParameter("scheduleVerification", schedulePostRecoveryVerification)
                        .setParameter("id", outboxId)
                        .setParameter("leaseToken", leaseToken)
                        .executeUpdate();
                if (updated == 0) {
                    return false;
                }
            } else {
                reQueuedDirty = false;
                int nextRetries = currentRetries + 1;
                exhausted = nextRetries >= outboxProperties.getMaxRetries();
                int delaySeconds = (int) computeBackoffSeconds(nextRetries);

                int updated = entityManager.createNativeQuery(PG_FAIL_CAS_SQL)
                        .setParameter("nextStatus", exhausted ? OutboxStatus.FAILED.name() : OutboxStatus.PENDING.name())
                        .setParameter("retryCount", nextRetries)
                        .setParameter("exhausted", exhausted)
                        .setParameter("delaySeconds", delaySeconds)
                        .setParameter("lastError", safeError)
                        .setParameter("scheduleVerification", schedulePostRecoveryVerification)
                        .setParameter("id", outboxId)
                        .setParameter("leaseToken", leaseToken)
                        .executeUpdate();
                if (updated == 0) {
                    return false;
                }
            }
        } else {
            Optional<KeycloakOutboxEventEntity> opt = outboxRepository.findById(outboxId);
            if (opt.isEmpty()) {
                return false;
            }
            KeycloakOutboxEventEntity entity = opt.get();
            if (entity.getStatus() != OutboxStatus.IN_PROGRESS || !leaseToken.equals(entity.getLeaseToken())) {
                return false;
            }
            targetVer = entity.getTargetVersion();
            pendingSince = entity.getPendingSince();
            Instant now = Instant.now();
            if (entity.isDirty()) {
                reQueuedDirty = true;
                entity.setStatus(OutboxStatus.PENDING);
                entity.setDirty(false);
                entity.setRetryCount(0);
                entity.setNextRetryAt(now);
                entity.setLastError(null);
                entity.setReclaimedVerificationPending(
                        entity.isReclaimedVerificationPending() || schedulePostRecoveryVerification);
                if (entity.getPendingSince() == null) {
                    entity.setPendingSince(now);
                }
            } else {
                reQueuedDirty = false;
                int nextRetries = entity.getRetryCount() + 1;
                exhausted = nextRetries >= outboxProperties.getMaxRetries();
                entity.setRetryCount(nextRetries);
                entity.setStatus(exhausted ? OutboxStatus.FAILED : OutboxStatus.PENDING);
                entity.setNextRetryAt(exhausted ? now : now.plusSeconds(computeBackoffSeconds(nextRetries)));
                entity.setLastError(safeError);
                entity.setReclaimedVerificationPending(
                        entity.isReclaimedVerificationPending() || schedulePostRecoveryVerification);
            }
            entity.setLeaseToken(null);
            entity.setLockedAt(null);
            entity.setLockedBy(null);
            entity.setUpdatedOn(now);
            outboxRepository.saveAndFlush(entity);
        }

        if (!reQueuedDirty && pendingSince != null
                && (jobType == OutboxJobType.RECONCILE_USER || jobType == OutboxJobType.RECONCILE_GROUP)) {
            long lagSeconds = Math.max(0L, Duration.between(pendingSince, Instant.now()).toSeconds());
            if (lagSeconds > SLA_LAG_THRESHOLD_SECONDS) {
                log.warn("Outbox reconcile SLA lag (>30s) during retry: tenant='{}', outboxId={}, ageSeconds={}",
                        ProblemDetailFactory.sanitizeForLog(currentTenantTag()), outboxId, lagSeconds);
            }
        }

        if (!reQueuedDirty && exhausted) {
            recordPermanentFailureMetricAndLog(outboxId, aggregateType, aggregateId, jobType, safeError);
            if (aggregateType == OutboxAggregateType.USER && jobType == OutboxJobType.RECONCILE_USER) {
                Long finalTargetVer = targetVer;
                userRepository.findById(aggregateId).ifPresent(user -> {
                    if (user.getSyncVersion() == null || finalTargetVer == null
                            || user.getSyncVersion() <= finalTargetVer) {
                        userRepository.updateSyncProgress(
                                aggregateId, null, 0L, SyncStatus.SYNC_FAILED, Instant.now());
                    }
                });
            }
        }
        return true;
    }

    @Transactional
    public boolean cancelActionEmailWithCas(long outboxId, UUID leaseToken, String reason) {
        if (leaseToken == null) {
            return false;
        }
        return entityManager.createQuery("""
                UPDATE KeycloakOutboxEventEntity e
                SET e.status = com.techsophy.tsf.account.outbox.OutboxStatus.CANCELLED,
                    e.leaseToken = null,
                    e.lockedAt = null,
                    e.lockedBy = null,
                    e.lastError = :reason,
                    e.updatedOn = :now
                WHERE e.id = :id
                  AND e.leaseToken = :leaseToken
                  AND e.status = com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                """)
                .setParameter("reason", sanitizeAndTruncateError(reason))
                .setParameter("now", Instant.now())
                .setParameter("id", outboxId)
                .setParameter("leaseToken", leaseToken)
                .executeUpdate() == 1;
    }

    @Transactional
    public boolean deferActionEmailWithCas(long outboxId, UUID leaseToken, Duration delay) {
        if (leaseToken == null) {
            return false;
        }
        Duration effectiveDelay = (delay == null || delay.isNegative()) ? Duration.ofSeconds(2) : delay;
        Instant now = Instant.now();
        return entityManager.createQuery("""
                UPDATE KeycloakOutboxEventEntity e
                SET e.status = com.techsophy.tsf.account.outbox.OutboxStatus.PENDING,
                    e.leaseToken = null,
                    e.lockedAt = null,
                    e.lockedBy = null,
                    e.nextRetryAt = :nextRetryAt,
                    e.updatedOn = :now
                WHERE e.id = :id
                  AND e.leaseToken = :leaseToken
                  AND e.status = com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                """)
                .setParameter("nextRetryAt", now.plus(effectiveDelay))
                .setParameter("now", now)
                .setParameter("id", outboxId)
                .setParameter("leaseToken", leaseToken)
                .executeUpdate() == 1;
    }

    @Transactional
    public boolean markActionEmailDispatchedWithCas(long outboxId, UUID leaseToken) {
        if (leaseToken == null) {
            return false;
        }
        Instant now = Instant.now();
        return entityManager.createQuery("""
                UPDATE KeycloakOutboxEventEntity e
                SET e.status = com.techsophy.tsf.account.outbox.OutboxStatus.DISPATCHED,
                    e.dispatchedAt = :now,
                    e.leaseToken = null,
                    e.lockedAt = null,
                    e.lockedBy = null,
                    e.lastError = null,
                    e.updatedOn = :now
                WHERE e.id = :id
                  AND e.leaseToken = :leaseToken
                  AND e.status = com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                """)
                .setParameter("now", now)
                .setParameter("id", outboxId)
                .setParameter("leaseToken", leaseToken)
                .executeUpdate() == 1;
    }

    @Transactional
    public boolean markActionEmailAmbiguousFailure(long outboxId, String errorMessage) {
        return entityManager.createQuery("""
                UPDATE KeycloakOutboxEventEntity e
                SET e.status = com.techsophy.tsf.account.outbox.OutboxStatus.FAILED,
                    e.ambiguousDelivery = true,
                    e.lastError = :error,
                    e.leaseToken = null,
                    e.lockedAt = null,
                    e.lockedBy = null,
                    e.updatedOn = :now
                WHERE e.id = :id
                  AND e.status = com.techsophy.tsf.account.outbox.OutboxStatus.DISPATCHED
                """)
                .setParameter("error", sanitizeAndTruncateError(errorMessage))
                .setParameter("now", Instant.now())
                .setParameter("id", outboxId)
                .executeUpdate() == 1;
    }

    @Transactional
    public KeycloakOutboxEventEntity resetFailedForRetry(long outboxId) {
        entityManager.flush();
        Instant now = Instant.now();
        int updated;
        if (isPostgreSql()) {
            updated = entityManager.createNativeQuery(PG_RESET_FAILED_CAS_SQL)
                    .setParameter("id", outboxId)
                    .executeUpdate();
        } else {
            updated = entityManager.createQuery("""
                    UPDATE KeycloakOutboxEventEntity e
                    SET e.status = com.techsophy.tsf.account.outbox.OutboxStatus.PENDING,
                        e.dirty = false,
                        e.retryCount = 0,
                        e.nextRetryAt = :now,
                        e.lastError = null,
                        e.leaseToken = null,
                        e.lockedAt = null,
                        e.lockedBy = null,
                        e.verificationRequiredAt = null,
                        e.reclaimedVerificationPending = false,
                        e.pendingSince = :now,
                        e.updatedOn = :now
                    WHERE e.id = :id
                      AND e.status = com.techsophy.tsf.account.outbox.OutboxStatus.FAILED
                      AND NOT (e.jobType = com.techsophy.tsf.account.outbox.OutboxJobType.SEND_ACTION_EMAIL
                               AND e.ambiguousDelivery = true)
                    """)
                    .setParameter("now", now)
                    .setParameter("id", outboxId)
                    .executeUpdate();
        }

        if (updated == 0) {
            KeycloakOutboxEventEntity existing = outboxRepository.findById(outboxId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.RESOURCE_NOT_FOUND,
                            "Outbox event '" + outboxId + "' was not found."
                    ));
            if (existing.getJobType() == OutboxJobType.SEND_ACTION_EMAIL && existing.isAmbiguousDelivery()) {
                throw new ApiException(
                        ApiErrorCode.INVALID_STATE_TRANSITION,
                        "Outbox action-email event '" + outboxId
                                + "' has ambiguous delivery state; trigger explicit resend-invite instead."
                );
            }
            throw new ApiException(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Outbox event '" + outboxId + "' is in status " + existing.getStatus()
                            + "; only FAILED events can be retried."
            );
        }

        entityManager.clear();
        KeycloakOutboxEventEntity saved = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Outbox event '" + outboxId + "' was not found."
                ));

        if (saved.getAggregateType() == OutboxAggregateType.USER
                && saved.getJobType() == OutboxJobType.RECONCILE_USER) {
            Instant syncUpdatedAt = saved.getUpdatedOn() != null ? saved.getUpdatedOn() : now;
            userRepository.updateSyncProgress(
                    saved.getAggregateId(), null, 0L, SyncStatus.PENDING_SYNC, syncUpdatedAt);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public long measurePendingReconcileLagSeconds() {
        Instant oldestPending;
        if (isPostgreSql()) {
            Object raw = entityManager.createNativeQuery("""
                    SELECT MIN(pending_since)
                    FROM tp_keycloak_outbox
                    WHERE job_type IN ('RECONCILE_USER', 'RECONCILE_GROUP')
                      AND status IN ('PENDING', 'IN_PROGRESS')
                      AND pending_since IS NOT NULL
                    """).getSingleResult();
            oldestPending = toInstant(raw);
        } else {
            oldestPending = entityManager.createQuery("""
                    SELECT MIN(e.pendingSince)
                    FROM KeycloakOutboxEventEntity e
                    WHERE e.jobType IN (
                        com.techsophy.tsf.account.outbox.OutboxJobType.RECONCILE_USER,
                        com.techsophy.tsf.account.outbox.OutboxJobType.RECONCILE_GROUP
                    )
                      AND e.status IN (
                        com.techsophy.tsf.account.outbox.OutboxStatus.PENDING,
                        com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                      )
                      AND e.pendingSince IS NOT NULL
                    """, Instant.class).getSingleResult();
        }
        long lagSeconds = oldestPending != null
                ? Math.max(0L, Duration.between(oldestPending, Instant.now()).toSeconds())
                : 0L;
        String tenantTag = currentTenantTag();
        AtomicLong lagHolder = tenantLagSeconds.computeIfAbsent(tenantTag, key -> {
            AtomicLong holder = new AtomicLong(0L);
            if (meterRegistry != null) {
                Gauge.builder(OUTBOX_OLDEST_LAG_METRIC, holder, AtomicLong::get)
                        .description("Age in seconds of the oldest un-synced RECONCILE outbox job per tenant")
                        .tag("tenant", key)
                        .register(meterRegistry);
            }
            return holder;
        });
        lagHolder.set(lagSeconds);
        if (lagSeconds > SLA_LAG_THRESHOLD_SECONDS) {
            if (meterRegistry != null) {
                meterRegistry.counter(OUTBOX_SLA_BREACH_METRIC, "tenant", tenantTag).increment();
            }
            log.warn("Outbox reconcile SLA breached (>30s): tenant='{}', oldestPendingAgeSeconds={}",
                    ProblemDetailFactory.sanitizeForLog(tenantTag), lagSeconds);
        }
        return lagSeconds;
    }

    long computeBackoffSeconds(int retryCount) {
        long backoff = Math.max(1L, outboxProperties.getBaseBackoffSeconds());
        long max = Math.max(backoff, outboxProperties.getMaxBackoffSeconds());
        for (int i = 1; i < retryCount && backoff < max; i++) {
            backoff = backoff > (max / 2L) ? max : backoff * 2L;
        }
        return Math.min(backoff, max);
    }

    private List<ClaimedOutboxJob> claimNextJobsPostgres(int limit, Duration leaseTtl, String workerId) {
        @SuppressWarnings("unchecked")
        List<Object[]> candidates = entityManager.createNativeQuery(PG_CLAIM_SELECT_SQL)
                .setParameter("leaseTtlSeconds", (int) Math.max(1L, leaseTtl.toSeconds()))
                .setParameter("limit", limit)
                .getResultList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(candidates.size());
        Map<Long, Boolean> reclaimedById = new LinkedHashMap<>(candidates.size());
        for (Object[] candidate : candidates) {
            long id = ((Number) candidate[0]).longValue();
            String prevStatus = String.valueOf(candidate[1]);
            boolean wasReclaimed = OutboxStatus.IN_PROGRESS.name().equals(prevStatus);
            UUID leaseToken = UUID.randomUUID();
            entityManager.createNativeQuery(PG_CLAIM_UPDATE_SQL)
                    .setParameter("leaseToken", leaseToken)
                    .setParameter("workerId", workerId)
                    .setParameter("wasReclaimed", wasReclaimed)
                    .setParameter("id", id)
                    .executeUpdate();
            ids.add(id);
            reclaimedById.put(id, wasReclaimed);
        }
        Map<Long, KeycloakOutboxEventEntity> byId = new LinkedHashMap<>();
        for (KeycloakOutboxEventEntity entity : outboxRepository.findAllById(ids)) {
            byId.put(entity.getId(), entity);
        }
        List<ClaimedOutboxJob> claimed = new ArrayList<>(ids.size());
        for (Long id : ids) {
            KeycloakOutboxEventEntity entity = byId.get(id);
            if (entity != null) {
                claimed.add(new ClaimedOutboxJob(entity, Boolean.TRUE.equals(reclaimedById.get(id))));
            }
        }
        return claimed;
    }

    private List<ClaimedOutboxJob> claimNextJobsPortable(int limit, Duration leaseTtl, String workerId) {
        Instant now = Instant.now();
        Instant expiredCutoff = now.minus(leaseTtl);
        List<KeycloakOutboxEventEntity> candidates = entityManager.createQuery("""
                SELECT e FROM KeycloakOutboxEventEntity e
                WHERE (e.status = com.techsophy.tsf.account.outbox.OutboxStatus.PENDING
                       AND e.nextRetryAt <= :now)
                   OR (e.status = com.techsophy.tsf.account.outbox.OutboxStatus.COMPLETED
                       AND e.verificationRequiredAt IS NOT NULL
                       AND e.verificationRequiredAt <= :now)
                   OR (e.status = com.techsophy.tsf.account.outbox.OutboxStatus.IN_PROGRESS
                       AND e.lockedAt < :expiredCutoff)
                ORDER BY CASE WHEN e.jobType IN (
                            com.techsophy.tsf.account.outbox.OutboxJobType.RECONCILE_USER,
                            com.techsophy.tsf.account.outbox.OutboxJobType.RECONCILE_GROUP)
                         THEN 0 ELSE 1 END,
                         e.nextRetryAt ASC,
                         e.createdOn ASC,
                         e.id ASC
                """, KeycloakOutboxEventEntity.class)
                .setParameter("now", now)
                .setParameter("expiredCutoff", expiredCutoff)
                .setMaxResults(limit)
                .getResultList();

        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ClaimedOutboxJob> claimed = new ArrayList<>(candidates.size());
        for (KeycloakOutboxEventEntity entity : candidates) {
            boolean wasReclaimed = entity.getStatus() == OutboxStatus.IN_PROGRESS;
            entity.setStatus(OutboxStatus.IN_PROGRESS);
            entity.setDirty(false);
            entity.setLeaseToken(UUID.randomUUID());
            entity.setLockedAt(now);
            entity.setLockedBy(workerId);
            entity.setVerificationRequiredAt(null);
            entity.setReclaimedVerificationPending(entity.isReclaimedVerificationPending() || wasReclaimed);
            entity.setUpdatedOn(now);
            KeycloakOutboxEventEntity saved = outboxRepository.saveAndFlush(entity);
            claimed.add(new ClaimedOutboxJob(saved, wasReclaimed));
        }
        return claimed;
    }

    private void upsertUserReconcilePortable(long userId, long targetVersion) {
        Instant now = Instant.now();
        Optional<KeycloakOutboxEventEntity> existingOpt = outboxRepository.findByAggregateTypeAndAggregateIdAndJobType(
                OutboxAggregateType.USER, userId, OutboxJobType.RECONCILE_USER);
        if (existingOpt.isPresent()) {
            applyPortableCoalesceUpdate(existingOpt.get(), targetVersion, now);
            return;
        }
        KeycloakOutboxEventEntity fresh = new KeycloakOutboxEventEntity();
        fresh.setId(idGenerator.nextId().longValue());
        fresh.setJobType(OutboxJobType.RECONCILE_USER);
        fresh.setAggregateType(OutboxAggregateType.USER);
        fresh.setAggregateId(userId);
        fresh.setTargetVersion(targetVersion);
        fresh.setDirty(false);
        fresh.setReclaimedVerificationPending(false);
        fresh.setPendingSince(now);
        fresh.setStatus(OutboxStatus.PENDING);
        fresh.setRetryCount(0);
        fresh.setNextRetryAt(now);
        fresh.setCreatedOn(now);
        fresh.setUpdatedOn(now);
        try {
            outboxRepository.saveAndFlush(fresh);
        } catch (DataIntegrityViolationException ex) {
            KeycloakOutboxEventEntity concurrent = outboxRepository.findByAggregateTypeAndAggregateIdAndJobType(
                    OutboxAggregateType.USER, userId, OutboxJobType.RECONCILE_USER).orElseThrow(() -> ex);
            applyPortableCoalesceUpdate(concurrent, targetVersion, now);
        }
    }

    private void applyPortableCoalesceUpdate(KeycloakOutboxEventEntity existing, long targetVersion, Instant now) {
        long currentTarget = existing.getTargetVersion() != null ? existing.getTargetVersion() : 0L;
        boolean inProgress = existing.getStatus() == OutboxStatus.IN_PROGRESS;
        boolean alreadyPendingOrInProgress = inProgress || existing.getStatus() == OutboxStatus.PENDING;
        existing.setTargetVersion(Math.max(currentTarget, targetVersion));
        existing.setDirty(inProgress);
        if (!inProgress && existing.getVerificationRequiredAt() != null) {
            existing.setReclaimedVerificationPending(true);
        }
        existing.setPendingSince(alreadyPendingOrInProgress
                ? (existing.getPendingSince() != null ? existing.getPendingSince() : now)
                : now);
        existing.setStatus(inProgress ? OutboxStatus.IN_PROGRESS : OutboxStatus.PENDING);
        existing.setVerificationRequiredAt(null);
        if (!inProgress) {
            existing.setRetryCount(0);
            existing.setNextRetryAt(now);
        }
        existing.setLastError(null);
        existing.setUpdatedOn(now);
        outboxRepository.saveAndFlush(existing);
    }

    private void recordPermanentFailureMetricAndLog(
            long outboxId,
            OutboxAggregateType aggregateType,
            long aggregateId,
            OutboxJobType jobType,
            String safeError) {
        String tenantTag = currentTenantTag();
        if (meterRegistry != null) {
            meterRegistry.counter(
                    OUTBOX_SYNC_FAILED_METRIC,
                    "tenant", tenantTag,
                    "job_type", jobType.name()
            ).increment();
        }
        log.error("Outbox job permanently FAILED after max retries: tenant='{}', outboxId={}, jobType={}, aggregateType={}, aggregateId={}, error='{}'",
                ProblemDetailFactory.sanitizeForLog(tenantTag),
                outboxId,
                jobType,
                aggregateType,
                aggregateId,
                safeError);
    }

    private Duration effectiveLeaseDuration(Duration leaseTtl) {
        return (leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero())
                ? Duration.ofSeconds(outboxProperties.getLeaseTtlSeconds())
                : leaseTtl;
    }

    private boolean isPostgreSql() {
        Boolean cached = this.postgresDatabase;
        if (cached != null) {
            return cached;
        }
        Session session = entityManager.unwrap(Session.class);
        String product = session.doReturningWork(conn -> conn.getMetaData().getDatabaseProductName());
        boolean isPg = product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        this.postgresDatabase = isPg;
        return isPg;
    }

    private String normalizeWorkerId(String workerId) {
        String raw = (workerId == null || workerId.isBlank()) ? outboxProperties.getWorkerId() : workerId.trim();
        return raw.length() > MAX_WORKER_ID_LENGTH ? raw.substring(0, MAX_WORKER_ID_LENGTH) : raw;
    }

    private static String currentTenantTag() {
        String tenant = TenantContext.getTenantId();
        return (tenant == null || tenant.isBlank()) ? TenantContext.DEFAULT_TENANT : tenant;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant inst) {
            return inst;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }

    private static String normalizeNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private static String sanitizeAndTruncateError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Keycloak synchronization failed.";
        }
        String sanitized = raw.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", " ").trim();
        return sanitized.length() > MAX_ERROR_LENGTH
                ? sanitized.substring(0, MAX_ERROR_LENGTH)
                : sanitized;
    }
}
