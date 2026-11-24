package com.techsophy.tsf.account.outbox;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.IdGeneratorConfig;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.ProvisioningMode;
import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserRepository;
import com.techsophy.tsf.account.user.UserStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({IdGeneratorConfig.class, OutboxProperties.class, OutboxStore.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class OutboxStoreTest {

    @Autowired
    private OutboxStore outboxStore;

    @Autowired
    private OutboxProperties outboxProperties;

    @Autowired
    private KeycloakOutboxRepository outboxRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdGeneratorImpl idGenerator;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void outboxPropertiesShouldEnforceDefaultsValidationBoundsAndWorkerIdFallback() {
        OutboxProperties props = new OutboxProperties();
        assertThat(props.isWorkerEnabled()).isTrue();
        assertThat(props.getPollIntervalMs()).isEqualTo(2000L);
        assertThat(props.getLeaseTtlSeconds()).isEqualTo(60);
        assertThat(props.getVerificationDelaySeconds()).isEqualTo(15);
        assertThat(props.getSyncRevokeTimeoutMs()).isEqualTo(5000L);
        assertThat(props.getMaxRetries()).isEqualTo(5);
        assertThat(props.getBatchSize()).isEqualTo(20);
        assertThat(props.getBaseBackoffSeconds()).isEqualTo(5);
        assertThat(props.getMaxBackoffSeconds()).isEqualTo(300);
        assertThat(props.getWorkerId()).isNotBlank().hasSizeLessThanOrEqualTo(100);

        props.setWorkerId("   ");
        assertThat(props.getWorkerId()).isNotBlank().hasSizeLessThanOrEqualTo(100);
        props.setWorkerId(null);
        assertThat(props.getWorkerId()).isNotBlank().hasSizeLessThanOrEqualTo(100);
        props.setWorkerId("w".repeat(120));
        assertThat(props.getWorkerId()).hasSize(100);
        props.setWorkerId("worker-pod-1");
        assertThat(props.getWorkerId()).isEqualTo("worker-pod-1");
        assertThat(props.isActionEmailDeliveryEnabled()).isFalse();
        props.setActionEmailDeliveryEnabled(true);
        assertThat(props.isActionEmailDeliveryEnabled()).isTrue();
        assertThat(OutboxProperties.formatWorkerId(null, "12345678-aaaa")).isEqualTo("worker-12345678");
        assertThat(OutboxProperties.formatWorkerId("   ", "12345678-aaaa")).isEqualTo("worker-12345678");
        assertThat(OutboxProperties.formatWorkerId("h".repeat(120), "12345678-aaaa")).hasSize(100);

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(props)).isEmpty();

        props.setPollIntervalMs(50L);
        props.setLeaseTtlSeconds(4);
        props.setVerificationDelaySeconds(0);
        props.setSyncRevokeTimeoutMs(10 * 1000L + 1L);
        props.setMaxRetries(0);
        props.setBatchSize(201);
        props.setBaseBackoffSeconds(0);
        props.setMaxBackoffSeconds(4);
        assertThat(validator.validate(props)).hasSize(8);
    }

    @Test
    void upsertUserReconcileShouldCoalesceSingleRowAndMarkDirtyWhenInProgress() {
        UserEntity user = createUser("alice", 1L, UserStatus.ACTIVE);

        outboxStore.upsertUserReconcile(user.getId(), 1L);
        outboxStore.upsertUserReconcile(user.getId(), 3L);
        // Out-of-order lower version must not downgrade targetVersion
        outboxStore.upsertUserReconcile(user.getId(), 2L);

        List<KeycloakOutboxEventEntity> rows = outboxRepository.findAll();
        assertThat(rows).hasSize(1);
        KeycloakOutboxEventEntity row = rows.getFirst();
        assertThat(row.getJobType()).isEqualTo(OutboxJobType.RECONCILE_USER);
        assertThat(row.getAggregateType()).isEqualTo(OutboxAggregateType.USER);
        assertThat(row.getAggregateId()).isEqualTo(user.getId());
        assertThat(row.getTargetVersion()).isEqualTo(3L);
        assertThat(row.isDirty()).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);

        // Claim row -> IN_PROGRESS
        List<ClaimedOutboxJob> claimed = outboxStore.claimNextJobs(10, Duration.ofSeconds(60), "worker-a");
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().wasReclaimed()).isFalse();
        UUID originalLease = claimed.getFirst().event().getLeaseToken();
        assertThat(originalLease).isNotNull();

        // Mutation while IN_PROGRESS sets dirty=true without changing status or leaseToken
        outboxStore.upsertUserReconcile(user.getId(), 4L);
        KeycloakOutboxEventEntity dirtyRow = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(dirtyRow.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(dirtyRow.isDirty()).isTrue();
        assertThat(dirtyRow.getTargetVersion()).isEqualTo(4L);
        assertThat(dirtyRow.getLeaseToken()).isEqualTo(originalLease);
    }

    @Test
    void enqueueActionEmailShouldCreateDiscreteRowsWithoutCollidingWithReconcileRow() {
        UserEntity user = createUser("bob", 1L, UserStatus.ACTIVE);
        outboxStore.upsertUserReconcile(user.getId(), 1L);

        long emailId1 = outboxStore.enqueueActionEmail(
                user.getId(), java.util.Arrays.asList(null, "UPDATE_PASSWORD", "VERIFY_EMAIL"), 3600);
        long emailId2 = outboxStore.enqueueActionEmail(user.getId(), List.of("UPDATE_PASSWORD"), 1800);

        assertThat(emailId1).isNotEqualTo(emailId2);
        assertThat(outboxRepository.findAll()).hasSize(3);

        KeycloakOutboxEventEntity emailRow = outboxRepository.findById(emailId1).orElseThrow();
        assertThat(emailRow.getJobType()).isEqualTo(OutboxJobType.SEND_ACTION_EMAIL);
        assertThat(emailRow.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(emailRow.getPayload()).containsEntry("lifespanSeconds", 3600);
        assertThat(emailRow.getPayload().get("actions")).isInstanceOf(List.class);

        assertThatThrownBy(() -> outboxStore.enqueueActionEmail(user.getId(), List.of(), 3600))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outboxStore.enqueueActionEmail(user.getId(), null, 3600))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outboxStore.enqueueActionEmail(user.getId(), List.of("  "), 3600))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outboxStore.enqueueActionEmail(user.getId(), List.of("VERIFY_EMAIL"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimNextJobsShouldRespectDueTimesLeaseExpiryVerificationPassAndLimits() {
        UserEntity u1 = createUser("u1", 1L, UserStatus.ACTIVE);
        UserEntity u2 = createUser("u2", 1L, UserStatus.ACTIVE);
        UserEntity u3 = createUser("u3", 1L, UserStatus.ACTIVE);
        UserEntity u4 = createUser("u4", 1L, UserStatus.ACTIVE);

        // u1: future PENDING (must NOT claim)
        KeycloakOutboxEventEntity futurePending = newEvent(u1.getId(), OutboxStatus.PENDING,
                Instant.now().plusSeconds(120), null, null, null, false);
        // u2: expired IN_PROGRESS with dirty=true (must reclaim with wasReclaimed=true & dirty=false)
        KeycloakOutboxEventEntity expiredLease = newEvent(u2.getId(), OutboxStatus.IN_PROGRESS,
                Instant.now().minusSeconds(10), Instant.now().minusSeconds(90), UUID.randomUUID(), null, true);
        // u3: active IN_PROGRESS (must NOT claim)
        KeycloakOutboxEventEntity activeLease = newEvent(u3.getId(), OutboxStatus.IN_PROGRESS,
                Instant.now().minusSeconds(10), Instant.now().minusSeconds(10), UUID.randomUUID(), null, false);
        // u4: COMPLETED with due verification_required_at (must claim with wasReclaimed=false)
        KeycloakOutboxEventEntity dueVerify = newEvent(u4.getId(), OutboxStatus.COMPLETED,
                Instant.now().minusSeconds(60), null, null, Instant.now().minusSeconds(1), false);
        outboxRepository.saveAllAndFlush(List.of(futurePending, expiredLease, activeLease, dueVerify));

        assertThat(outboxStore.claimNextJobs(0, Duration.ofSeconds(60), "worker-1")).isEmpty();

        List<ClaimedOutboxJob> claimed = outboxStore.claimNextJobs(10, Duration.ofSeconds(60), "worker-1");
        assertThat(claimed).hasSize(2);

        ClaimedOutboxJob reclaimedJob = claimed.stream()
                .filter(c -> c.event().getId().equals(expiredLease.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(reclaimedJob.wasReclaimed()).isTrue();
        assertThat(reclaimedJob.event().isDirty()).isFalse();
        assertThat(reclaimedJob.event().getLeaseToken()).isNotEqualTo(expiredLease.getLeaseToken());
        assertThat(reclaimedJob.event().getLockedBy()).isEqualTo("worker-1");

        ClaimedOutboxJob verifyJob = claimed.stream()
                .filter(c -> c.event().getId().equals(dueVerify.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(verifyJob.wasReclaimed()).isFalse();
        assertThat(verifyJob.event().getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(verifyJob.event().getVerificationRequiredAt()).isNull();
    }

    @Test
    void completeWithCasShouldRejectStaleLeaseHandleDirtyRequeueAndScheduleReclaimedVerification() {
        UserEntity user = createUser("carol", 1L, UserStatus.ACTIVE);
        outboxStore.upsertUserReconcile(user.getId(), 1L);

        List<ClaimedOutboxJob> firstClaim = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "worker-1");
        ClaimedOutboxJob job1 = firstClaim.getFirst();

        // Stale lease token must fail CAS and leave tp_user untouched
        boolean staleResult = outboxStore.completeWithCas(
                job1.event().getId(), UUID.randomUUID(), user.getId(), 1L, "kc-sub-stale", false);
        assertThat(staleResult).isFalse();
        UserEntity unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getSyncedVersion()).isEqualTo(0L);
        assertThat(unchangedUser.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);

        // Simulate dirty mutation while IN_PROGRESS -> completes straight back to PENDING
        user.setSyncVersion(2L);
        userRepository.saveAndFlush(user);
        outboxStore.upsertUserReconcile(user.getId(), 2L);

        CasCompletionResult dirtyCompletion = outboxStore.completeReconcileWithCas(
                job1.event().getId(),
                job1.event().getLeaseToken(),
                user.getId(),
                OutboxAggregateType.USER,
                1L,
                "kc-sub-1",
                false,
                false
        );
        assertThat(dirtyCompletion.casUpdated()).isTrue();
        assertThat(dirtyCompletion.reQueuedBecauseDirty()).isTrue();

        KeycloakOutboxEventEntity reQueued = outboxRepository.findById(job1.event().getId()).orElseThrow();
        assertThat(reQueued.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reQueued.isDirty()).isFalse();
        assertThat(reQueued.getLeaseToken()).isNull();
        UserEntity partialUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(partialUser.getKeycloakSub()).isEqualTo("kc-sub-1");
        assertThat(partialUser.getSyncedVersion()).isEqualTo(1L);
        assertThat(partialUser.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);

        // Claim again and simulate reclaimed completion (wasReclaimed=true) -> schedules +verificationDelaySeconds
        ClaimedOutboxJob secondClaim = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "worker-2").getFirst();
        Instant beforeComplete = Instant.now();
        boolean reclaimedComplete = outboxStore.completeWithCas(
                secondClaim.event().getId(),
                secondClaim.event().getLeaseToken(),
                user.getId(),
                2L,
                "kc-sub-1",
                true
        );
        assertThat(reclaimedComplete).isTrue();

        KeycloakOutboxEventEntity completedVerify = outboxRepository.findById(reQueued.getId()).orElseThrow();
        assertThat(completedVerify.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(completedVerify.getVerificationRequiredAt())
                .isAfter(beforeComplete.plusSeconds(outboxProperties.getVerificationDelaySeconds() - 2L));
        UserEntity syncedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(syncedUser.getSyncedVersion()).isEqualTo(2L);
        assertThat(syncedUser.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        // Fenced completion must not advance syncedVersion and must NOT mark outbox COMPLETED
        outboxStore.upsertUserReconcile(user.getId(), 5L);
        ClaimedOutboxJob fencedClaim = outboxStore.claimNextJobs(5, Duration.ZERO, "  ").getFirst();
        assertThat(outboxStore.completeReconcileWithCas(
                fencedClaim.event().getId(),
                fencedClaim.event().getLeaseToken(),
                user.getId(),
                OutboxAggregateType.USER,
                5L,
                "kc-sub-1",
                false,
                true
        ).casUpdated()).isTrue();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSyncedVersion()).isEqualTo(2L);
        assertThat(outboxRepository.findById(fencedClaim.event().getId()).orElseThrow().getStatus())
                .isNotEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    void markRetryOrFailedWithCasShouldRequeueDirtyImmediatelyAndCompleteShouldResetRetryCount() {
        UserEntity user = createUser("dirty.retry", 1L, UserStatus.ACTIVE);
        outboxStore.upsertUserReconcile(user.getId(), 1L);

        // Drive retry_count to maxRetries - 1 (4)
        for (int i = 0; i < outboxProperties.getMaxRetries() - 1; i++) {
            KeycloakOutboxEventEntity row = outboxRepository.findAll().getFirst();
            row.setNextRetryAt(Instant.now().minusSeconds(1));
            outboxRepository.saveAndFlush(row);
            ClaimedOutboxJob c = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
            assertThat(outboxStore.markRetryOrFailedWithCas(
                    c.event().getId(), c.event().getLeaseToken(), user.getId(),
                    OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "transient")).isTrue();
        }

        // Claim attempt 5 while a concurrent mutation (v2) marks dirty = TRUE
        KeycloakOutboxEventEntity row = outboxRepository.findAll().getFirst();
        row.setNextRetryAt(Instant.now().minusSeconds(1));
        outboxRepository.saveAndFlush(row);
        ClaimedOutboxJob finalAttempt = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
        user.setSyncVersion(2L);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        userRepository.saveAndFlush(user);
        outboxStore.upsertUserReconcile(user.getId(), 2L);

        // Failing v1 on attempt 5 MUST re-queue dirty v2 as PENDING with retry_count = 0 (never FAILED / SYNC_FAILED)
        assertThat(outboxStore.markRetryOrFailedWithCas(
                finalAttempt.event().getId(), finalAttempt.event().getLeaseToken(), user.getId(),
                OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "kc error on v1")).isTrue();

        KeycloakOutboxEventEntity afterDirtyFail = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(afterDirtyFail.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterDirtyFail.isDirty()).isFalse();
        assertThat(afterDirtyFail.getRetryCount()).isZero();
        assertThat(afterDirtyFail.getLastError()).isNull();
        assertThat(afterDirtyFail.getTargetVersion()).isEqualTo(2L);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSyncStatus())
                .isEqualTo(SyncStatus.PENDING_SYNC);

        // Drive retry_count to 3 then complete -> completeReconcileWithCas MUST reset retry_count = 0
        for (int i = 0; i < 3; i++) {
            afterDirtyFail.setNextRetryAt(Instant.now().minusSeconds(1));
            outboxRepository.saveAndFlush(afterDirtyFail);
            ClaimedOutboxJob c = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
            assertThat(outboxStore.markRetryOrFailedWithCas(
                    c.event().getId(), c.event().getLeaseToken(), user.getId(),
                    OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "err")).isTrue();
            afterDirtyFail = outboxRepository.findById(row.getId()).orElseThrow();
        }
        afterDirtyFail.setNextRetryAt(Instant.now().minusSeconds(1));
        outboxRepository.saveAndFlush(afterDirtyFail);
        ClaimedOutboxJob recoverClaim = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
        assertThat(outboxStore.completeWithCas(
                recoverClaim.event().getId(), recoverClaim.event().getLeaseToken(),
                user.getId(), 2L, "kc-sub-dirty", false)).isTrue();
        KeycloakOutboxEventEntity completed = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(completed.getRetryCount()).isZero();
    }

    @Test
    void markRetryOrFailedWithCasShouldApplyBoundedBackoffSanitizeErrorAndRespectCas() {
        UserEntity user = createUser("dave", 1L, UserStatus.ACTIVE);
        outboxStore.upsertUserReconcile(user.getId(), 1L);

        assertThat(outboxStore.markRetryOrFailedWithCas(
                1L, null, user.getId(), OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER, " ")).isFalse();
        assertThat(outboxStore.completeWithCas(
                1L, null, user.getId(), 1L, "sub", false)).isFalse();
        assertThat(outboxStore.cancelActionEmailWithCas(1L, null, "reason")).isFalse();
        assertThat(outboxStore.deferActionEmailWithCas(1L, null, null)).isFalse();
        assertThat(outboxStore.markActionEmailDispatchedWithCas(1L, null)).isFalse();
        assertThat(outboxStore.claimNextJobs(5, Duration.ofSeconds(-5), "w".repeat(120))).isNotEmpty();

        // Wrong status / GROUP / SEND_ACTION_EMAIL / negative duration / null targetVersion branches on H2
        KeycloakOutboxEventEntity pendingRow = outboxRepository.findAll().getFirst();
        pendingRow.setStatus(OutboxStatus.PENDING);
        pendingRow.setTargetVersion(null);
        pendingRow.setLeaseToken(null);
        outboxRepository.saveAndFlush(pendingRow);
        assertThat(outboxStore.completeReconcileWithCas(
                pendingRow.getId(), UUID.randomUUID(), user.getId(), OutboxAggregateType.USER, 1L, null, false, false)
                .casUpdated()).isFalse();
        assertThat(outboxStore.markRetryOrFailedWithCas(
                pendingRow.getId(), UUID.randomUUID(), user.getId(), OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER, "err")).isFalse();

        ClaimedOutboxJob reClaim = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
        assertThat(outboxStore.deferActionEmailWithCas(
                reClaim.event().getId(), reClaim.event().getLeaseToken(), Duration.ofSeconds(-2))).isTrue();
        KeycloakOutboxEventEntity deferredRow = outboxRepository.findById(reClaim.event().getId()).orElseThrow();
        deferredRow.setNextRetryAt(Instant.now().minusSeconds(1));
        outboxRepository.saveAndFlush(deferredRow);
        reClaim = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
        assertThat(outboxStore.completeReconcileWithCas(
                reClaim.event().getId(), reClaim.event().getLeaseToken(), user.getId(), OutboxAggregateType.GROUP,
                1L, null, false, false).casUpdated()).isTrue();

        long failedEmailId = outboxStore.enqueueActionEmail(user.getId(), List.of("VERIFY_EMAIL"), 900);
        ClaimedOutboxJob emailClaim = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1").getFirst();
        outboxProperties.setMaxRetries(1);
        assertThat(outboxStore.markRetryOrFailedWithCas(
                emailClaim.event().getId(), emailClaim.event().getLeaseToken(), user.getId(),
                OutboxAggregateType.USER, OutboxJobType.SEND_ACTION_EMAIL, "fail")).isTrue();
        outboxProperties.setMaxRetries(5);
        assertThat(outboxStore.resetFailedForRetry(failedEmailId).getStatus()).isEqualTo(OutboxStatus.PENDING);

        outboxRepository.deleteAll();
        assertThat(outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "w1")).isEmpty();
        outboxStore.upsertUserReconcile(user.getId(), 1L);

        // Stale lease and blank error fallback coverage
        KeycloakOutboxEventEntity row = outboxRepository.findAll().getFirst();
        assertThat(outboxStore.markRetryOrFailedWithCas(
                row.getId(), UUID.randomUUID(), user.getId(), OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER, null)).isFalse();

        // Walk retries 1..maxRetries
        String longCrlfError = "KC fail\r\nforged " + "x".repeat(2500);
        for (int attempt = 1; attempt <= outboxProperties.getMaxRetries(); attempt++) {
            row.setNextRetryAt(Instant.now().minusSeconds(1));
            outboxRepository.saveAndFlush(row);

            ClaimedOutboxJob claimed = outboxStore.claimNextJobs(5, Duration.ofSeconds(60), "worker-1").getFirst();
            Instant beforeFail = Instant.now();
            boolean updated = outboxStore.markRetryOrFailedWithCas(
                    claimed.event().getId(),
                    claimed.event().getLeaseToken(),
                    user.getId(),
                    OutboxAggregateType.USER,
                    OutboxJobType.RECONCILE_USER,
                    longCrlfError
            );
            assertThat(updated).isTrue();

            row = outboxRepository.findById(row.getId()).orElseThrow();
            assertThat(row.getRetryCount()).isEqualTo(attempt);
            assertThat(row.getLastError()).hasSize(2000).doesNotContain("\r", "\n");
            if (attempt < outboxProperties.getMaxRetries()) {
                assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
                long expectedBackoff = Math.min(
                        outboxProperties.getMaxBackoffSeconds(),
                        (long) outboxProperties.getBaseBackoffSeconds() << (attempt - 1)
                );
                assertThat(row.getNextRetryAt()).isAfterOrEqualTo(beforeFail.plusSeconds(expectedBackoff - 2L));
            } else {
                assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
            }
        }

        UserEntity failedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(failedUser.getSyncStatus()).isEqualTo(SyncStatus.SYNC_FAILED);
    }

    @Test
    void actionEmailCasHelpersShouldDeferCancelDispatchAndMarkAmbiguousFailureSafely() {
        UserEntity user = createUser("eve", 1L, UserStatus.ACTIVE);
        long id1 = outboxStore.enqueueActionEmail(user.getId(), List.of("VERIFY_EMAIL"), 900);
        long id2 = outboxStore.enqueueActionEmail(user.getId(), List.of("UPDATE_PASSWORD"), 900);
        long id3 = outboxStore.enqueueActionEmail(user.getId(), List.of("UPDATE_PASSWORD"), 900);

        List<ClaimedOutboxJob> claimed = outboxStore.claimNextJobs(10, Duration.ofSeconds(60), "worker-1");
        assertThat(claimed).hasSize(3);

        UUID lease1 = claimed.stream().filter(c -> c.event().getId().equals(id1)).findFirst().orElseThrow()
                .event().getLeaseToken();
        UUID lease2 = claimed.stream().filter(c -> c.event().getId().equals(id2)).findFirst().orElseThrow()
                .event().getLeaseToken();
        UUID lease3 = claimed.stream().filter(c -> c.event().getId().equals(id3)).findFirst().orElseThrow()
                .event().getLeaseToken();

        assertThat(outboxStore.deferActionEmailWithCas(id1, UUID.randomUUID(), Duration.ofSeconds(2))).isFalse();
        assertThat(outboxStore.deferActionEmailWithCas(id1, lease1, Duration.ofSeconds(5))).isTrue();
        KeycloakOutboxEventEntity deferred = outboxRepository.findById(id1).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(deferred.getRetryCount()).isZero();

        assertThat(outboxStore.cancelActionEmailWithCas(id2, lease2, "User disabled")).isTrue();
        KeycloakOutboxEventEntity cancelled = outboxRepository.findById(id2).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OutboxStatus.CANCELLED);

        assertThat(outboxStore.markActionEmailDispatchedWithCas(id3, lease3)).isTrue();
        KeycloakOutboxEventEntity dispatched = outboxRepository.findById(id3).orElseThrow();
        assertThat(dispatched.getStatus()).isEqualTo(OutboxStatus.DISPATCHED);
        assertThat(dispatched.getDispatchedAt()).isNotNull();

        assertThat(outboxStore.markActionEmailAmbiguousFailure(id3, "Read timed out")).isTrue();
        KeycloakOutboxEventEntity ambiguous = outboxRepository.findById(id3).orElseThrow();
        assertThat(ambiguous.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(ambiguous.isAmbiguousDelivery()).isTrue();
    }

    @Test
    void resetFailedForRetryShouldEnforceStateAndAmbiguousDeliveryGuards() {
        UserEntity user = createUser("frank", 1L, UserStatus.ACTIVE);
        user.setSyncStatus(SyncStatus.SYNC_FAILED);
        userRepository.saveAndFlush(user);

        KeycloakOutboxEventEntity failedReconcile = newEvent(user.getId(), OutboxStatus.FAILED,
                Instant.now().minusSeconds(30), null, null, null, false);
        failedReconcile.setRetryCount(5);
        failedReconcile.setLastError("Transient 502");
        outboxRepository.saveAndFlush(failedReconcile);

        KeycloakOutboxEventEntity ambiguousEmail = newEvent(user.getId(), OutboxStatus.FAILED,
                Instant.now(), null, null, null, false);
        ambiguousEmail.setJobType(OutboxJobType.SEND_ACTION_EMAIL);
        ambiguousEmail.setAmbiguousDelivery(true);
        outboxRepository.saveAndFlush(ambiguousEmail);

        KeycloakOutboxEventEntity completedRow = newEvent(user.getId() + 100L, OutboxStatus.COMPLETED,
                Instant.now(), null, null, null, false);
        outboxRepository.saveAndFlush(completedRow);

        assertThatThrownBy(() -> outboxStore.resetFailedForRetry(999_999L))
                .isInstanceOf(com.techsophy.tsf.account.error.ApiException.class)
                .satisfies(ex -> assertThat(((com.techsophy.tsf.account.error.ApiException) ex).errorCode())
                        .isEqualTo(com.techsophy.tsf.account.error.ApiErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> outboxStore.resetFailedForRetry(completedRow.getId()))
                .isInstanceOf(com.techsophy.tsf.account.error.ApiException.class)
                .satisfies(ex -> assertThat(((com.techsophy.tsf.account.error.ApiException) ex).errorCode())
                        .isEqualTo(com.techsophy.tsf.account.error.ApiErrorCode.INVALID_STATE_TRANSITION));

        assertThatThrownBy(() -> outboxStore.resetFailedForRetry(ambiguousEmail.getId()))
                .isInstanceOf(com.techsophy.tsf.account.error.ApiException.class)
                .satisfies(ex -> assertThat(((com.techsophy.tsf.account.error.ApiException) ex).errorCode())
                        .isEqualTo(com.techsophy.tsf.account.error.ApiErrorCode.INVALID_STATE_TRANSITION));

        KeycloakOutboxEventEntity reset = outboxStore.resetFailedForRetry(failedReconcile.getId());
        assertThat(reset.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reset.getRetryCount()).isZero();
        assertThat(reset.getLastError()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSyncStatus())
                .isEqualTo(SyncStatus.PENDING_SYNC);
    }

    @Test
    void postgresNativeSqlBranchesShouldExecuteExpectedNativeUpsertAndClaimQueries() {
        EntityManager mockEm = mock(EntityManager.class);
        org.hibernate.Session mockSession = mock(org.hibernate.Session.class);
        when(mockEm.unwrap(org.hibernate.Session.class)).thenReturn(mockSession);
        when(mockSession.doReturningWork(any())).thenReturn("PostgreSQL");

        Query upsertQuery = mock(Query.class);
        when(upsertQuery.setParameter(anyString(), any())).thenReturn(upsertQuery);
        when(upsertQuery.executeUpdate()).thenReturn(1);

        Query claimSelectQuery = mock(Query.class);
        when(claimSelectQuery.setParameter(anyString(), any())).thenReturn(claimSelectQuery);
        when(claimSelectQuery.getResultList()).thenReturn(
                List.<Object[]>of(new Object[]{101L, "IN_PROGRESS"}),
                List.of()
        );

        Query claimUpdateQuery = mock(Query.class);
        when(claimUpdateQuery.setParameter(anyString(), any())).thenReturn(claimUpdateQuery);
        when(claimUpdateQuery.executeUpdate()).thenReturn(1);

        Query lockQuery = mock(Query.class);
        when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
        when(lockQuery.getResultList()).thenReturn(
                List.<Object[]>of(new Object[]{Boolean.FALSE, 2L, 4}),
                List.<Object[]>of(new Object[]{Boolean.FALSE, 2L, 4}),
                List.of(),
                List.of(),
                List.<Object[]>of(new Object[]{Boolean.TRUE, 3L, 0}),
                List.<Object[]>of(new Object[]{Boolean.FALSE, null, 0}),
                List.<Object[]>of(new Object[]{Boolean.FALSE, null, 0})
        );

        Query completeUpdateQuery = mock(Query.class);
        when(completeUpdateQuery.setParameter(anyString(), any())).thenReturn(completeUpdateQuery);
        when(completeUpdateQuery.executeUpdate()).thenReturn(1, 1, 0, 1, 0, 1, 1);

        when(mockEm.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("ON CONFLICT")) {
                return upsertQuery;
            }
            if (sql.contains("FOR UPDATE SKIP LOCKED")) {
                return claimSelectQuery;
            }
            if (sql.contains("SET status = 'IN_PROGRESS'")) {
                return claimUpdateQuery;
            }
            if (sql.contains("SELECT dirty, target_version")) {
                return lockQuery;
            }
            return completeUpdateQuery;
        });

        KeycloakOutboxRepository mockOutboxRepo = mock(KeycloakOutboxRepository.class);
        KeycloakOutboxEventEntity claimedEntity = new KeycloakOutboxEventEntity();
        claimedEntity.setId(101L);
        claimedEntity.setJobType(OutboxJobType.RECONCILE_USER);
        claimedEntity.setAggregateType(OutboxAggregateType.USER);
        claimedEntity.setAggregateId(77L);
        claimedEntity.setStatus(OutboxStatus.IN_PROGRESS);
        claimedEntity.setLeaseToken(UUID.randomUUID());
        when(mockOutboxRepo.findAllById(List.of(101L))).thenReturn(List.of(claimedEntity));
        when(mockOutboxRepo.saveAndFlush(any(KeycloakOutboxEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        claimedEntity.setStatus(OutboxStatus.FAILED);
        when(mockOutboxRepo.findById(101L)).thenReturn(java.util.Optional.of(claimedEntity));

        UserRepository mockUserRepo = mock(UserRepository.class);
        UserEntity pgUser = new UserEntity();
        pgUser.setId(77L);
        pgUser.setSyncVersion(2L);
        when(mockUserRepo.findById(77L)).thenReturn(java.util.Optional.of(pgUser));
        OutboxStore pgStore = new OutboxStore(mockEm, mockOutboxRepo, mockUserRepo, idGenerator, outboxProperties);

        pgStore.upsertUserReconcile(77L, 2L);
        verify(upsertQuery).executeUpdate();

        List<ClaimedOutboxJob> claimed = pgStore.claimNextJobs(5, Duration.ofSeconds(60), "pg-worker");
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().wasReclaimed()).isTrue();
        verify(claimUpdateQuery).executeUpdate();
        assertThat(pgStore.claimNextJobs(5, Duration.ofSeconds(60), "pg-worker")).isEmpty();

        CasCompletionResult res = pgStore.completeReconcileWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.USER,
                2L, "sub-77", true, false);
        assertThat(res.casUpdated()).isTrue();
        assertThat(res.reQueuedBecauseDirty()).isFalse();
        verify(mockUserRepo).updateSyncProgress(any(), any(), any(Long.class), any(), any());

        boolean failedPg = pgStore.markRetryOrFailedWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER, "KC unavailable");
        assertThat(failedPg).isTrue();

        boolean stalePg = pgStore.markRetryOrFailedWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER, "KC unavailable");
        assertThat(stalePg).isFalse();

        // Empty lock & 0-update & dirty & non-exhausted retry branches on PG
        assertThat(pgStore.completeReconcileWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.USER,
                2L, null, false, false).casUpdated()).isFalse();
        assertThat(pgStore.completeReconcileWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.GROUP,
                2L, null, false, false).casUpdated()).isFalse();
        assertThat(pgStore.markRetryOrFailedWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.GROUP,
                OutboxJobType.RECONCILE_GROUP, "err")).isTrue();
        assertThat(pgStore.markRetryOrFailedWithCas(
                101L, claimedEntity.getLeaseToken(), 77L, OutboxAggregateType.GROUP,
                OutboxJobType.RECONCILE_GROUP, "err")).isFalse();

        pgStore.enqueueActionEmail(77L, List.of("VERIFY_EMAIL"), 900);
        pgStore.resetFailedForRetry(101L);

        // H2 non-existent row + concurrent DataIntegrityViolationException recovery
        assertThat(outboxStore.completeReconcileWithCas(
                999_991L, UUID.randomUUID(), 77L, OutboxAggregateType.USER, 1L, null, false, false).casUpdated())
                .isFalse();
        assertThat(outboxStore.markRetryOrFailedWithCas(
                999_991L, UUID.randomUUID(), 77L, OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "e"))
                .isFalse();

        OutboxStore h2ConflictStore = new OutboxStore(mockEm, mockOutboxRepo, mockUserRepo, idGenerator, outboxProperties);
        when(mockSession.doReturningWork(any())).thenReturn("H2");
        when(mockOutboxRepo.findByAggregateTypeAndAggregateIdAndJobType(
                OutboxAggregateType.USER, 88L, OutboxJobType.RECONCILE_USER))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(claimedEntity));
        when(mockOutboxRepo.saveAndFlush(any(KeycloakOutboxEventEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("race"))
                .thenAnswer(inv -> inv.getArgument(0));
        h2ConflictStore.upsertUserReconcile(88L, 5L);
    }

    @Test
    void specificClaimReleaseDirtyVerificationCarryoverAtomicResetAndMetricsShouldWorkAcrossH2AndPg() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        EntityManager h2Em = org.mockito.Mockito.mock(EntityManager.class);
        org.hibernate.Session h2Session = org.mockito.Mockito.mock(org.hibernate.Session.class);
        when(h2Em.unwrap(org.hibernate.Session.class)).thenReturn(h2Session);
        when(h2Session.doReturningWork(any())).thenReturn("H2");

        // Use Spring-managed outboxStore (connected to H2) for DB state machine, plus a metric-instrumented store for SLA checks
        UserEntity user = createUser("lease_user", 1L, UserStatus.ACTIVE);
        assertThat(outboxStore.claimSpecificUserReconcileJob(user.getId(), Duration.ofSeconds(60), "sync-w"))
                .isEmpty();

        outboxStore.upsertUserReconcile(user.getId(), 1L);
        ClaimedOutboxJob claimed = outboxStore.claimSpecificUserReconcileJob(
                user.getId(), Duration.ofSeconds(60), "sync-w").orElseThrow();
        assertThat(claimed.wasReclaimed()).isFalse();
        assertThat(claimed.event().isReclaimedVerificationPending()).isFalse();

        // Contention while lease active returns empty
        assertThat(outboxStore.claimSpecificUserReconcileJob(
                user.getId(), Duration.ofSeconds(60), "sync-w-2")).isEmpty();

        // Wrong lease release returns false; valid lease releases back to PENDING cleanly
        assertThat(outboxStore.releaseReconcileToPendingWithCas(claimed.event().getId(), UUID.randomUUID())).isFalse();
        assertThat(outboxStore.releaseReconcileToPendingWithCas(
                claimed.event().getId(), claimed.event().getLeaseToken())).isTrue();
        KeycloakOutboxEventEntity released = outboxRepository.findById(claimed.event().getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(released.getLeaseToken()).isNull();

        // Reclaim expired IN_PROGRESS lease sets reclaimedVerificationPending=true and carries across dirty retry!
        released.setStatus(OutboxStatus.IN_PROGRESS);
        released.setLockedAt(Instant.now().minusSeconds(300));
        released.setLeaseToken(UUID.randomUUID());
        outboxRepository.saveAndFlush(released);

        ClaimedOutboxJob reclaimed = outboxStore.claimSpecificUserReconcileJob(
                user.getId(), Duration.ofSeconds(60), "reclaim-w").orElseThrow();
        assertThat(reclaimed.wasReclaimed()).isTrue();
        assertThat(reclaimed.event().isReclaimedVerificationPending()).isTrue();

        // Simulate concurrent mutation setting dirty=TRUE while IN_PROGRESS -> failure resets retryCount=0 & stays PENDING
        outboxStore.upsertUserReconcile(user.getId(), 2L);
        assertThat(outboxStore.markRetryOrFailedWithCas(
                reclaimed.event().getId(),
                reclaimed.event().getLeaseToken(),
                user.getId(),
                OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER,
                "transient error",
                true)).isTrue();
        KeycloakOutboxEventEntity afterDirtyFail = outboxRepository.findById(reclaimed.event().getId()).orElseThrow();
        assertThat(afterDirtyFail.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterDirtyFail.getRetryCount()).isEqualTo(0);
        assertThat(afterDirtyFail.isDirty()).isFalse();
        assertThat(afterDirtyFail.isReclaimedVerificationPending()).isTrue();

        // Complete non-reclaimed follow-up pass -> verificationRequiredAt must still be scheduled!
        ClaimedOutboxJob followUp = outboxStore.claimSpecificUserReconcileJob(
                user.getId(), Duration.ofSeconds(60), "sync-w").orElseThrow();
        assertThat(followUp.wasReclaimed()).isFalse();
        CasCompletionResult followDone = outboxStore.completeReconcileWithCas(
                followUp.event().getId(),
                followUp.event().getLeaseToken(),
                user.getId(),
                OutboxAggregateType.USER,
                2L,
                "kc-lease-sub",
                false,
                false);
        assertThat(followDone.casUpdated()).isTrue();
        assertThat(followDone.reQueuedBecauseDirty()).isFalse();
        KeycloakOutboxEventEntity completedRow = outboxRepository.findById(followUp.event().getId()).orElseThrow();
        assertThat(completedRow.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(completedRow.getVerificationRequiredAt()).isNotNull();
        assertThat(completedRow.isReclaimedVerificationPending()).isFalse();

        // Atomic retry guard: non-FAILED row throws INVALID_STATE_TRANSITION
        assertThatThrownBy(() -> outboxStore.resetFailedForRetry(completedRow.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION);

        // PG mocked branch coverage for new SQL paths + Micrometer lag/failure metrics
        EntityManager mockPgEm = mock(EntityManager.class);
        org.hibernate.Session mockPgSession = mock(org.hibernate.Session.class);
        when(mockPgEm.unwrap(org.hibernate.Session.class)).thenReturn(mockPgSession);
        when(mockPgSession.doReturningWork(any())).thenReturn("PostgreSQL");
        KeycloakOutboxRepository mockPgRepo = mock(KeycloakOutboxRepository.class);
        UserRepository mockPgUserRepo = mock(UserRepository.class);

        UUID pgLease = UUID.randomUUID();
        KeycloakOutboxEventEntity pgEvent = newEvent(78L, OutboxStatus.IN_PROGRESS, Instant.now(), Instant.now(), pgLease, null, false);
        pgEvent.setId(202L);
        pgEvent.setRetryCount(4);
        pgEvent.setPendingSince(Instant.now().minusSeconds(45));

        Query specificSelectQ = mock(Query.class);
        when(specificSelectQ.setParameter(anyString(), any())).thenReturn(specificSelectQ);
        when(specificSelectQ.getResultList()).thenReturn(List.<Object[]>of(new Object[]{202L, "IN_PROGRESS"}));

        Query pgUpdateQ = mock(Query.class);
        when(pgUpdateQ.setParameter(anyString(), any())).thenReturn(pgUpdateQ);
        when(pgUpdateQ.executeUpdate()).thenReturn(1);

        Query pgLockQ = mock(Query.class);
        when(pgLockQ.setParameter(anyString(), any())).thenReturn(pgLockQ);
        when(pgLockQ.getResultList()).thenReturn(
                List.<Object[]>of(new Object[]{Boolean.TRUE, 2L, 4, Boolean.TRUE, Timestamp.from(Instant.now().minusSeconds(45))}),
                List.<Object[]>of(new Object[]{Boolean.FALSE, 2L, 4, Boolean.TRUE, Timestamp.from(Instant.now().minusSeconds(45))})
        );

        Query pgLagQ = mock(Query.class);
        when(pgLagQ.getSingleResult()).thenReturn(Timestamp.from(Instant.now().minusSeconds(45)));

        when(mockPgEm.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("AND aggregate_id = :aggregateId") && sql.contains("FOR UPDATE SKIP LOCKED")) {
                return specificSelectQ;
            }
            if (sql.contains("SELECT dirty, target_version")) {
                return pgLockQ;
            }
            if (sql.contains("MIN(pending_since)")) {
                return pgLagQ;
            }
            return pgUpdateQ;
        });
        when(mockPgRepo.findAllById(List.of(202L))).thenReturn(List.of(pgEvent));
        when(mockPgRepo.findById(202L)).thenReturn(Optional.of(pgEvent));

        OutboxStore pgInstrumented = new OutboxStore(
                mockPgEm, mockPgRepo, mockPgUserRepo, idGenerator, outboxProperties, metrics);
        assertThat(pgInstrumented.claimSpecificUserReconcileJob(78L, Duration.ofSeconds(60), "pg-sync"))
                .isPresent();
        assertThat(pgInstrumented.releaseReconcileToPendingWithCas(202L, pgLease)).isTrue();
        assertThat(pgInstrumented.markRetryOrFailedWithCas(
                202L, pgLease, 78L, OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "dirty-retry", true))
                .isTrue();
        assertThat(pgInstrumented.markRetryOrFailedWithCas(
                202L, pgLease, 78L, OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "perm-fail", false))
                .isTrue();
        assertThat(metrics.get("awgment_outbox_sync_failed_total").counter().count()).isEqualTo(1.0);
        assertThat(pgInstrumented.measurePendingReconcileLagSeconds()).isGreaterThanOrEqualTo(40L);
        assertThat(metrics.get("awgment_outbox_sla_breach_total").counter().count()).isGreaterThanOrEqualTo(1.0);
        pgInstrumented.resetFailedForRetry(202L);
        verify(mockPgUserRepo).updateSyncProgress(
                org.mockito.ArgumentMatchers.eq(78L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(SyncStatus.PENDING_SYNC),
                any(Instant.class));

        // Additional edge branches: null lease release, H2 wrong-status/null-pendingSince markRetry, PG dirty complete + toInstant variants
        assertThat(outboxStore.releaseReconcileToPendingWithCas(101L, null)).isFalse();
        KeycloakOutboxEventEntity pendingNonInProgress = outboxRepository.saveAndFlush(newEvent(
                user.getId(), OutboxStatus.PENDING, Instant.now(), Instant.now(), UUID.randomUUID(), null, true));
        assertThat(outboxStore.markRetryOrFailedWithCas(
                pendingNonInProgress.getId(), pendingNonInProgress.getLeaseToken(),
                user.getId(), OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "not-in-prog", false))
                .isFalse();
        UUID dirtyLease = UUID.randomUUID();
        KeycloakOutboxEventEntity nullPendingDirty = newEvent(
                user.getId() + 1L, OutboxStatus.IN_PROGRESS, Instant.now(), Instant.now(), dirtyLease, null, true);
        nullPendingDirty.setPendingSince(null);
        nullPendingDirty.setJobType(OutboxJobType.SEND_ACTION_EMAIL);
        outboxRepository.saveAndFlush(nullPendingDirty);
        assertThat(outboxStore.markRetryOrFailedWithCas(
                nullPendingDirty.getId(), dirtyLease, user.getId() + 1L,
                OutboxAggregateType.USER, OutboxJobType.SEND_ACTION_EMAIL, "e", false)).isTrue();

        when(pgLockQ.getResultList()).thenReturn(
                List.<Object[]>of(new Object[]{Boolean.FALSE, 5L, 0, Boolean.FALSE, null}),
                List.<Object[]>of(new Object[]{Boolean.TRUE, 2L, 0, Boolean.FALSE, null})
        );
        when(pgUpdateQ.executeUpdate()).thenReturn(1, 0, 0);
        assertThat(pgInstrumented.completeReconcileWithCas(
                202L, pgLease, 78L, OutboxAggregateType.USER, 2L, "kc-78", true, false)
                .reQueuedBecauseDirty()).isTrue();
        assertThat(pgInstrumented.markRetryOrFailedWithCas(
                202L, pgLease, 78L, OutboxAggregateType.USER, OutboxJobType.RECONCILE_USER, "err", false))
                .isFalse();
        assertThat(pgInstrumented.releaseReconcileToPendingWithCas(202L, pgLease)).isFalse();

        when(pgLagQ.getSingleResult()).thenReturn(
                null,
                Instant.now().minusSeconds(10),
                java.time.OffsetDateTime.now().minusSeconds(5),
                "unexpected-type");
        assertThat(pgInstrumented.measurePendingReconcileLagSeconds()).isZero();
        assertThat(pgInstrumented.measurePendingReconcileLagSeconds()).isGreaterThanOrEqualTo(9L);
        assertThat(pgInstrumented.measurePendingReconcileLagSeconds()).isGreaterThanOrEqualTo(4L);
        assertThat(pgInstrumented.measurePendingReconcileLagSeconds()).isZero();
    }

    private UserEntity createUser(String userName, long syncVersion, UserStatus status) {
        UserEntity user = new UserEntity();
        user.setId(idGenerator.nextId().longValue());
        user.setUserName(userName);
        user.setEmailId(userName + "@" + "acme.invalid");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setLocale("en");
        user.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
        user.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        user.setStatus(status);
        user.setSyncVersion(syncVersion);
        user.setSyncedVersion(0L);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setCreatedBy("test");
        user.setUpdatedBy("test");
        return userRepository.saveAndFlush(user);
    }

    private KeycloakOutboxEventEntity newEvent(
            long userId,
            OutboxStatus status,
            Instant nextRetryAt,
            Instant lockedAt,
            UUID leaseToken,
            Instant verificationRequiredAt,
            boolean dirty) {
        KeycloakOutboxEventEntity entity = new KeycloakOutboxEventEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setJobType(OutboxJobType.RECONCILE_USER);
        entity.setAggregateType(OutboxAggregateType.USER);
        entity.setAggregateId(userId);
        entity.setTargetVersion(1L);
        entity.setDirty(dirty);
        entity.setStatus(status);
        entity.setNextRetryAt(nextRetryAt);
        entity.setLockedAt(lockedAt);
        entity.setLeaseToken(leaseToken);
        entity.setVerificationRequiredAt(verificationRequiredAt);
        entity.setRetryCount(0);
        entity.setCreatedOn(Instant.now());
        entity.setUpdatedOn(Instant.now());
        return entity;
    }
}
