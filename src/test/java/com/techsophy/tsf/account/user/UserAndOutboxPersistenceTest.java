package com.techsophy.tsf.account.user;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.IdGeneratorConfig;
import com.techsophy.tsf.account.idp.TrustedIdpEntity;
import com.techsophy.tsf.account.idp.TrustedIdpRepository;
import com.techsophy.tsf.account.outbox.KeycloakOutboxEventEntity;
import com.techsophy.tsf.account.outbox.KeycloakOutboxRepository;
import com.techsophy.tsf.account.outbox.OutboxAggregateType;
import com.techsophy.tsf.account.outbox.OutboxJobType;
import com.techsophy.tsf.account.outbox.OutboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(IdGeneratorConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class UserAndOutboxPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrustedIdpRepository trustedIdpRepository;

    @Autowired
    private KeycloakOutboxRepository outboxRepository;

    @Autowired
    private IdGeneratorImpl idGenerator;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        userRepository.deleteAll();
        trustedIdpRepository.deleteAll();
    }

    @Test
    void shouldPersistUserEntityWithJsonbFieldsOptimisticLockingAndCaseInsensitiveQueries() {
        String aliceEmail = "Alice.Admin" + "@" + "Acme.Invalid";

        UserEntity alice = new UserEntity();
        alice.setId(idGenerator.nextId().longValue());
        alice.setKeycloakSub("sub-alice-1");
        alice.setProvisioningKey("direct:1");
        alice.setUserName("alice.admin");
        alice.setEmailId(aliceEmail);
        alice.setFirstName("Alice");
        alice.setLastName("Admin");
        alice.setMobileNumber("+1-555-0101");
        alice.setDepartment("Engineering");
        alice.setTimezone("UTC");
        alice.setSelfServicePreferences(Map.of("compactMode", true));
        alice.setAttributes(Map.of("costCenter", "ENG-42"));
        alice.setStatus(UserStatus.ACTIVE);
        alice.setDesiredRoles(List.of("editor", "viewer"));
        alice.setDesiredGroups(List.of("1001", "1002"));
        alice.setCreatedBy("operator-sub");
        alice.setUpdatedBy("operator-sub");

        UserEntity savedAlice = userRepository.saveAndFlush(alice);
        assertThat(savedAlice.getLocale()).isEqualTo("en");
        assertThat(savedAlice.getIdentitySource()).isEqualTo(IdentitySource.KEYCLOAK_LOCAL);
        assertThat(savedAlice.getProvisioningMode()).isEqualTo(ProvisioningMode.DIRECT_OPERATOR);
        assertThat(savedAlice.getSyncVersion()).isEqualTo(1L);
        assertThat(savedAlice.getSyncedVersion()).isZero();
        assertThat(savedAlice.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);
        assertThat(savedAlice.getOptLockVersion()).isZero();
        assertThat(savedAlice.getCreatedAt()).isNotNull();
        assertThat(savedAlice.getUpdatedAt()).isNotNull();
        assertThat(savedAlice.getDesiredRoles()).containsExactly("editor", "viewer");
        assertThat(savedAlice.getDesiredGroups()).containsExactly("1001", "1002");
        assertThat(savedAlice.getAttributes()).containsEntry("costCenter", "ENG-42");
        assertThat(savedAlice.getSelfServicePreferences()).containsEntry("compactMode", true);

        // Case-insensitive & sub lookups
        assertThat(userRepository.findByKeycloakSub("sub-alice-1")).isPresent();
        assertThat(userRepository.findByKeycloakSub("missing-sub")).isEmpty();
        assertThat(userRepository.findByUserNameIgnoreCase("ALICE.ADMIN")).isPresent();
        assertThat(userRepository.findByEmailIdIgnoreCase("alice.admin" + "@" + "acme.invalid")).isPresent();

        // Active / Disabled existence checks + exclude self ID
        assertThat(userRepository.existsActiveOrDisabledByUserName("ALICE.ADMIN")).isTrue();
        assertThat(userRepository.existsActiveOrDisabledByUserNameAndIdNot("ALICE.ADMIN", savedAlice.getId())).isFalse();
        assertThat(userRepository.existsActiveOrDisabledByUserNameAndIdNot("ALICE.ADMIN", savedAlice.getId() + 1)).isTrue();
        assertThat(userRepository.existsActiveOrDisabledByEmail("alice.admin" + "@" + "acme.invalid")).isTrue();
        assertThat(userRepository.existsActiveOrDisabledByEmailAndIdNot(
                "alice.admin" + "@" + "acme.invalid", savedAlice.getId())).isFalse();
        assertThat(userRepository.existsActiveOrDisabledByEmailAndIdNot(
                "alice.admin" + "@" + "acme.invalid", savedAlice.getId() + 1)).isTrue();

        // PENDING_APPROVAL and REJECTED users do not squat on ACTIVE/DISABLED uniqueness, while DISABLED does
        UserEntity pending = new UserEntity();
        pending.setId(idGenerator.nextId().longValue());
        pending.setUserName("pending.user");
        pending.setEmailId("pending" + "@" + "acme.invalid");
        pending.setStatus(UserStatus.PENDING_APPROVAL);
        pending.setLocale("  ");
        pending.setSelfServicePreferences(null);
        pending.setAttributes(null);
        pending.setIdentitySource(null);
        pending.setProvisioningMode(null);
        pending.setSyncStatus(null);
        pending.setSyncVersion(null);
        pending.setSyncedVersion(null);
        UserEntity savedPending = userRepository.saveAndFlush(pending);
        assertThat(savedPending.getLocale()).isEqualTo("en");
        assertThat(savedPending.getSelfServicePreferences()).isEmpty();
        assertThat(savedPending.getAttributes()).isEmpty();
        assertThat(savedPending.getIdentitySource()).isEqualTo(IdentitySource.KEYCLOAK_LOCAL);
        assertThat(savedPending.getProvisioningMode()).isEqualTo(ProvisioningMode.DIRECT_OPERATOR);
        assertThat(savedPending.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);
        assertThat(savedPending.getSyncVersion()).isEqualTo(1L);
        assertThat(savedPending.getSyncedVersion()).isZero();
        assertThat(savedPending.getDesiredRoles()).isNull();
        assertThat(savedPending.getDesiredGroups()).isNull();

        assertThat(userRepository.existsActiveOrDisabledByUserName("pending.user")).isFalse();
        assertThat(userRepository.existsActiveOrDisabledByEmail("pending" + "@" + "acme.invalid")).isFalse();

        savedPending.setStatus(UserStatus.REJECTED);
        savedPending.setRejectionReason("Not authorized");
        userRepository.saveAndFlush(savedPending);
        assertThat(userRepository.existsActiveOrDisabledByUserName("pending.user")).isFalse();

        UserEntity disabled = new UserEntity();
        disabled.setId(idGenerator.nextId().longValue());
        disabled.setUserName("disabled.user");
        disabled.setEmailId("disabled" + "@" + "acme.invalid");
        disabled.setIdentitySource(IdentitySource.EXTERNAL_IDP);
        disabled.setIdpAlias("entra-acme");
        disabled.setEmailVerifiedAtStaging(true);
        disabled.setIdpVerifiedAtStaging(true);
        disabled.setProvisioningMode(ProvisioningMode.JIT_AUTO_APPROVED);
        disabled.setStatus(UserStatus.DISABLED);
        disabled.setLocale(null);
        Instant fixed = Instant.parse("2026-02-01T00:00:00Z");
        disabled.setCreatedAt(fixed);
        disabled.setUpdatedAt(fixed);
        UserEntity savedDisabled = userRepository.saveAndFlush(disabled);
        assertThat(savedDisabled.getLocale()).isEqualTo("en");
        assertThat(savedDisabled.getCreatedAt()).isEqualTo(fixed);
        assertThat(savedDisabled.isEmailVerifiedAtStaging()).isTrue();
        assertThat(savedDisabled.isIdpVerifiedAtStaging()).isTrue();
        assertThat(userRepository.existsActiveOrDisabledByUserName("DISABLED.USER")).isTrue();
        assertThat(userRepository.existsActiveOrDisabledByEmail("DISABLED" + "@" + "acme.invalid")).isTrue();

        // @Version optimistic locking conflict
        UserEntity staleCopy = userRepository.findById(savedAlice.getId()).orElseThrow();
        savedAlice.setDepartment("Platform");
        userRepository.saveAndFlush(savedAlice);

        staleCopy.setDepartment("StaleDept");
        assertThatThrownBy(() -> userRepository.saveAndFlush(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // JpaSpecificationExecutor works cleanly
        Specification<UserEntity> activeSpec = (root, query, cb) -> cb.equal(root.get("status"), UserStatus.ACTIVE);
        assertThat(userRepository.findAll(activeSpec, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1L);
    }

    @Test
    void shouldUpdateSyncProgressFencingConcurrentMutationsAndFailures() {
        UserEntity user = new UserEntity();
        user.setId(idGenerator.nextId().longValue());
        user.setUserName("sync.user");
        user.setEmailId("sync" + "@" + "acme.invalid");
        user.setStatus(UserStatus.ACTIVE);
        user.setSyncVersion(2L);
        user.setSyncedVersion(0L);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        UserEntity saved = userRepository.saveAndFlush(user);

        // 1. Worker finishes v1 while DB already moved to v2 -> syncedVersion advances to 1, syncStatus remains PENDING_SYNC
        Instant t1 = Instant.parse("2026-02-01T01:00:00Z");
        int updated1 = userRepository.updateSyncProgress(saved.getId(), "kc-sub-1", 1L, SyncStatus.SYNCED, t1);
        assertThat(updated1).isEqualTo(1);

        UserEntity afterV1 = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterV1.getKeycloakSub()).isEqualTo("kc-sub-1");
        assertThat(afterV1.getSyncedVersion()).isEqualTo(1L);
        assertThat(afterV1.getSyncStatus()).isEqualTo(SyncStatus.PENDING_SYNC);

        // 2. Worker finishes v2 with null keycloakSub (COALESCE keeps existing sub) -> syncStatus becomes SYNCED
        Instant t2 = Instant.parse("2026-02-01T01:01:00Z");
        int updated2 = userRepository.updateSyncProgress(saved.getId(), null, 2L, SyncStatus.SYNCED, t2);
        assertThat(updated2).isEqualTo(1);

        UserEntity afterV2 = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterV2.getKeycloakSub()).isEqualTo("kc-sub-1");
        assertThat(afterV2.getSyncedVersion()).isEqualTo(2L);
        assertThat(afterV2.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        // 3. Stale call with lower syncedVersion (1L < 2L) never regresses syncedVersion
        int updated3 = userRepository.updateSyncProgress(saved.getId(), null, 1L, SyncStatus.SYNCED, t2);
        assertThat(updated3).isEqualTo(1);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getSyncedVersion()).isEqualTo(2L);

        // 4. Permanent failure updates syncStatus to SYNC_FAILED without regressing syncedVersion
        int updated4 = userRepository.updateSyncProgress(saved.getId(), null, 0L, SyncStatus.SYNC_FAILED, t2);
        assertThat(updated4).isEqualTo(1);
        UserEntity afterFail = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterFail.getSyncStatus()).isEqualTo(SyncStatus.SYNC_FAILED);
        assertThat(afterFail.getSyncedVersion()).isEqualTo(2L);

        // 5. Reset back to PENDING_SYNC and verify null syncStatus guard
        int updated5 = userRepository.updateSyncProgress(saved.getId(), null, 0L, SyncStatus.PENDING_SYNC, t2);
        assertThat(updated5).isEqualTo(1);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getSyncStatus())
                .isEqualTo(SyncStatus.PENDING_SYNC);
        assertThatThrownBy(() -> userRepository.updateSyncProgress(saved.getId(), null, 0L, null, t2))
                .isInstanceOf(NullPointerException.class);

        // 6. Missing user returns 0 rows
        assertThat(userRepository.updateSyncProgress(999L, null, 1L, SyncStatus.SYNCED, t2)).isZero();
    }

    @Test
    void shouldPersistTrustedIdpEntityAndQueryByAliasAndEnabledFlag() {
        TrustedIdpEntity idp = new TrustedIdpEntity();
        idp.setId(idGenerator.nextId().longValue());
        idp.setIdpAlias("entra-acme");
        idp.setExternalTenantId("tenant-guid-1");
        idp.setTenantClaimName(" ");
        idp.setEnabled(true);
        idp.setCreatedBy("approver-1");
        idp.setUpdatedBy("approver-1");

        TrustedIdpEntity saved = trustedIdpRepository.saveAndFlush(idp);
        assertThat(saved.getTenantClaimName()).isEqualTo("idp_tenant_id");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getOptLockVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        assertThat(trustedIdpRepository.findByIdpAlias("entra-acme")).isPresent();
        assertThat(trustedIdpRepository.findByIdpAlias("other-idp")).isEmpty();
        assertThat(trustedIdpRepository.existsByIdpAliasAndEnabledTrue("entra-acme")).isTrue();

        TrustedIdpEntity stale = trustedIdpRepository.findByIdpAlias("entra-acme").orElseThrow();
        saved.setEnabled(false);
        trustedIdpRepository.saveAndFlush(saved);
        assertThat(trustedIdpRepository.existsByIdpAliasAndEnabledTrue("entra-acme")).isFalse();

        stale.setExternalTenantId("stale-change");
        assertThatThrownBy(() -> trustedIdpRepository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Explicit null tenantClaimName defaults, and explicit timestamps/custom claim are preserved
        TrustedIdpEntity nullClaim = new TrustedIdpEntity();
        nullClaim.setId(idGenerator.nextId().longValue());
        nullClaim.setIdpAlias("okta-acme");
        nullClaim.setTenantClaimName(null);
        TrustedIdpEntity savedNullClaim = trustedIdpRepository.saveAndFlush(nullClaim);
        assertThat(savedNullClaim.getTenantClaimName()).isEqualTo("idp_tenant_id");

        Instant fixed = Instant.parse("2026-03-01T00:00:00Z");
        TrustedIdpEntity explicit = new TrustedIdpEntity();
        explicit.setId(idGenerator.nextId().longValue());
        explicit.setIdpAlias("google-acme");
        explicit.setTenantClaimName("hd");
        explicit.setCreatedAt(fixed);
        explicit.setUpdatedAt(fixed);
        TrustedIdpEntity savedExplicit = trustedIdpRepository.saveAndFlush(explicit);
        assertThat(savedExplicit.getTenantClaimName()).isEqualTo("hd");
        assertThat(savedExplicit.getCreatedAt()).isEqualTo(fixed);
        assertThat(savedExplicit.getUpdatedAt()).isEqualTo(fixed);
    }

    @Test
    void shouldPersistKeycloakOutboxEventsAndSupportAllRepositoryQueries() {
        KeycloakOutboxEventEntity reconcile = new KeycloakOutboxEventEntity();
        reconcile.setId(idGenerator.nextId().longValue());
        reconcile.setJobType(OutboxJobType.RECONCILE_USER);
        reconcile.setAggregateType(OutboxAggregateType.USER);
        reconcile.setAggregateId(501L);
        reconcile.setTargetVersion(3L);
        reconcile.setStatus(null);
        KeycloakOutboxEventEntity savedReconcile = outboxRepository.saveAndFlush(reconcile);

        assertThat(savedReconcile.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedReconcile.isDirty()).isFalse();
        assertThat(savedReconcile.isAmbiguousDelivery()).isFalse();
        assertThat(savedReconcile.getRetryCount()).isZero();
        assertThat(savedReconcile.getNextRetryAt()).isNotNull();
        assertThat(savedReconcile.getCreatedOn()).isNotNull();
        assertThat(savedReconcile.getUpdatedOn()).isNotNull();

        UUID leaseToken = UUID.randomUUID();
        KeycloakOutboxEventEntity email = new KeycloakOutboxEventEntity();
        email.setId(idGenerator.nextId().longValue());
        email.setJobType(OutboxJobType.SEND_ACTION_EMAIL);
        email.setAggregateType(OutboxAggregateType.USER);
        email.setAggregateId(502L);
        email.setPayload(Map.of("actions", List.of("UPDATE_PASSWORD"), "lifespanSeconds", 300));
        email.setAmbiguousDelivery(true);
        email.setStatus(OutboxStatus.FAILED);
        email.setLeaseToken(leaseToken);
        email.setLockedAt(Instant.now());
        email.setLockedBy("worker-1");
        email.setRetryCount(1);
        email.setDispatchedAt(Instant.now());
        email.setLastError("HTTP read timeout");
        KeycloakOutboxEventEntity savedEmail = outboxRepository.saveAndFlush(email);

        assertThat(savedEmail.getLeaseToken()).isEqualTo(leaseToken);
        assertThat(savedEmail.getPayload()).containsKey("actions");
        assertThat(savedEmail.isAmbiguousDelivery()).isTrue();

        assertThat(outboxRepository.findByAggregateTypeAndAggregateIdAndJobType(
                OutboxAggregateType.USER, 501L, OutboxJobType.RECONCILE_USER)).isPresent();
        assertThat(outboxRepository.findByAggregateTypeAndAggregateIdAndJobType(
                OutboxAggregateType.GROUP, 501L, OutboxJobType.RECONCILE_GROUP)).isEmpty();

        Page<KeycloakOutboxEventEntity> failedPage = outboxRepository.findByStatus(
                OutboxStatus.FAILED, PageRequest.of(0, 10));
        assertThat(failedPage.getTotalElements()).isEqualTo(1L);

        Page<KeycloakOutboxEventEntity> userAggPage = outboxRepository.findByAggregateType(
                OutboxAggregateType.USER, PageRequest.of(0, 10));
        assertThat(userAggPage.getTotalElements()).isEqualTo(2L);

        Page<KeycloakOutboxEventEntity> filteredPage = outboxRepository.findByStatusAndAggregateType(
                OutboxStatus.PENDING, OutboxAggregateType.USER, PageRequest.of(0, 10));
        assertThat(filteredPage.getTotalElements()).isEqualTo(1L);
        assertThat(filteredPage.getContent().getFirst().getAggregateId()).isEqualTo(501L);

        Specification<KeycloakOutboxEventEntity> ambiguousSpec =
                (root, query, cb) -> cb.isTrue(root.get("ambiguousDelivery"));
        assertThat(outboxRepository.findAll(ambiguousSpec)).hasSize(1);

        // Exercise @PreUpdate and explicit non-null timestamps on @PrePersist
        savedReconcile.setLastError("Transient retry notice");
        KeycloakOutboxEventEntity updatedReconcile = outboxRepository.saveAndFlush(savedReconcile);
        assertThat(updatedReconcile.getUpdatedOn()).isAfterOrEqualTo(savedReconcile.getCreatedOn());

        Instant fixed = Instant.parse("2026-04-01T00:00:00Z");
        KeycloakOutboxEventEntity explicitTimestamps = new KeycloakOutboxEventEntity();
        explicitTimestamps.setId(idGenerator.nextId().longValue());
        explicitTimestamps.setJobType(OutboxJobType.RECONCILE_GROUP);
        explicitTimestamps.setAggregateType(OutboxAggregateType.GROUP);
        explicitTimestamps.setAggregateId(700L);
        explicitTimestamps.setStatus(OutboxStatus.PENDING);
        explicitTimestamps.setCreatedOn(fixed);
        explicitTimestamps.setUpdatedOn(fixed);
        explicitTimestamps.setNextRetryAt(fixed);
        KeycloakOutboxEventEntity savedExplicit = outboxRepository.saveAndFlush(explicitTimestamps);
        assertThat(savedExplicit.getCreatedOn()).isEqualTo(fixed);
        assertThat(savedExplicit.getNextRetryAt()).isEqualTo(fixed);
    }
}
