package com.techsophy.tsf.account.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.keycloak.KeycloakUserSyncClient;
import com.techsophy.tsf.account.keycloak.ReconcileResult;
import com.techsophy.tsf.account.keycloak.UserReconcilePayload;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.outbox.ClaimedOutboxJob;
import com.techsophy.tsf.account.outbox.CoalescedOutboxWorker;
import com.techsophy.tsf.account.outbox.KeycloakOutboxEventEntity;
import com.techsophy.tsf.account.outbox.KeycloakOutboxRepository;
import com.techsophy.tsf.account.outbox.OutboxAggregateType;
import com.techsophy.tsf.account.outbox.OutboxJobType;
import com.techsophy.tsf.account.outbox.OutboxStatus;
import com.techsophy.tsf.account.outbox.OutboxStore;
import com.techsophy.tsf.account.tenant.TenantService;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.ProvisioningMode;
import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserRepository;
import com.techsophy.tsf.account.user.UserStatus;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-2 Testcontainers IT (PostgreSQL 16 + Keycloak 26.0.5) verifying Section 10 Scenario 1:
 * partial unique index coalescing, dirty in-flight flag, <= 10s KC timeout << 60s lease TTL,
 * expired lease reclaim + 15s forced verification pass, pre-write awgment_sync_version fencing,
 * atomic lease_token CAS protection, FOR UPDATE SKIP LOCKED concurrency, and UC-SYNC-01..02.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoalescedOutboxConcurrencyIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("awgment_account")
            .withUsername("awgment")
            .withPassword("awgment_test_pw");

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.5")
            .withFeaturesDisabled("organization")
            .withRealmImportFiles(
                    "keycloak/platform-realm.json",
                    "keycloak/tenant-acme-realm.json",
                    "keycloak/mock-entra-realm.json"
            );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/public");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("awgment.multitenancy.enabled", () -> "true");
        registry.add("awgment.flyway.migrate-on-startup", () -> "false");
        registry.add("awgment.outbox.worker-enabled", () -> "false");

        registry.add("awgment.security.keycloak-base-url", KEYCLOAK::getAuthServerUrl);
        registry.add("awgment.security.platform-realm", () -> "platform");
        registry.add("awgment.security.expected-audience", () -> "awgment-account");
        registry.add("awgment.security.trusted-client-id", () -> "awgment-account");

        registry.add("awgment.keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("awgment.keycloak.admin.realm", () -> "master");
        registry.add("awgment.keycloak.admin.client-id", () -> "admin-cli");
        registry.add("awgment.keycloak.admin.client-secret", () -> "");
        registry.add("awgment.keycloak.admin.username", KEYCLOAK::getAdminUsername);
        registry.add("awgment.keycloak.admin.password", KEYCLOAK::getAdminPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KeycloakOutboxRepository outboxRepository;

    @Autowired
    private OutboxStore outboxStore;

    @Autowired
    private CoalescedOutboxWorker worker;

    @Autowired
    private KeycloakUserSyncClient keycloakUserSyncClient;

    @Autowired
    private IdGeneratorImpl idGenerator;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @Order(1)
    void scenario1_pausedWorkerLeaseReclaimPreWriteFencingCasAnd15sVerificationHeal() throws Exception {
        tenantService.createTenant(
                new CreateTenantRequest("acme", "Acme Corp", null, null, null),
                "platform-admin-it"
        );

        TenantContext.setTenantId("acme");
        long userId = idGenerator.nextId().longValue();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUserName("scenario1_user");
        user.setEmailId("scenario1" + "@" + "acme.invalid");
        user.setFirstName("Scenario");
        user.setLastName("One");
        user.setLocale("en");
        user.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
        user.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        user.setStatus(UserStatus.ACTIVE);
        user.setDesiredRoles(List.of("awgment-user"));
        user.setSyncVersion(1L);
        user.setSyncedVersion(0L);
        user.setSyncStatus(SyncStatus.PENDING_SYNC);
        user.setCreatedBy("op-1");
        user.setUpdatedBy("op-1");
        userRepository.saveAndFlush(user);

        // Repeated upserts coalesce into a single row via partial unique index uq_tp_keycloak_outbox_coalesced_reconcile
        outboxStore.upsertUserReconcile(userId, 1L);
        outboxStore.upsertUserReconcile(userId, 1L);
        List<KeycloakOutboxEventEntity> initialRows = outboxRepository.findAll();
        assertThat(initialRows).hasSize(1);

        // 1. Worker 1 claims User A (ACTIVE, v1) and pauses past 60s lease expiry
        List<ClaimedOutboxJob> worker1Claim = outboxStore.claimNextJobs(20, Duration.ofSeconds(60), "worker-1");
        assertThat(worker1Claim).hasSize(1);
        ClaimedOutboxJob job1 = worker1Claim.getFirst();
        assertThat(job1.wasReclaimed()).isFalse();
        UUID worker1LeaseToken = job1.event().getLeaseToken();
        UserReconcilePayload worker1StalePayload = UserReconcilePayload.from(user);

        // 2. While Worker 1 is paused, DELETE /users/{id} disables User A (DISABLED, v2) and marks dirty=true
        UserEntity disabledUser = userRepository.findById(userId).orElseThrow();
        disabledUser.setStatus(UserStatus.DISABLED);
        disabledUser.setSyncVersion(2L);
        disabledUser.setSyncStatus(SyncStatus.PENDING_SYNC);
        userRepository.saveAndFlush(disabledUser);
        outboxStore.upsertUserReconcile(userId, 2L);

        KeycloakOutboxEventEntity inProgressDirty = outboxRepository.findById(job1.event().getId()).orElseThrow();
        assertThat(inProgressDirty.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(inProgressDirty.isDirty()).isTrue();
        assertThat(inProgressDirty.getTargetVersion()).isEqualTo(2L);
        assertThat(inProgressDirty.getLeaseToken()).isEqualTo(worker1LeaseToken);

        // Simulate 65s pause (> 60s lease TTL)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox SET locked_at = NOW() - INTERVAL '65 seconds' WHERE id = ?")) {
            ps.setLong(1, job1.event().getId());
            ps.executeUpdate();
        }

        // 3. Worker 2 reclaims expired lease, writes DISABLED v2 to live Keycloak, schedules 15s verification
        int reclaimedCount = worker.processTenantQueue("acme", 20);
        assertThat(reclaimedCount).isEqualTo(1);

        TenantContext.setTenantId("acme");
        UserEntity afterWorker2User = userRepository.findById(userId).orElseThrow();
        String kcSub = afterWorker2User.getKeycloakSub();
        assertThat(kcSub).isNotBlank();
        assertThat(afterWorker2User.getSyncedVersion()).isEqualTo(2L);
        assertThat(afterWorker2User.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        KeycloakOutboxEventEntity afterWorker2Outbox = outboxRepository.findById(job1.event().getId()).orElseThrow();
        assertThat(afterWorker2Outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(afterWorker2Outbox.getVerificationRequiredAt()).isAfter(Instant.now().plusSeconds(5));

        var kcAdmin = KEYCLOAK.getKeycloakAdminClient();
        UserRepresentation kcAfterWorker2 = kcAdmin.realm("acme").users().get(kcSub).toRepresentation();
        assertThat(kcAfterWorker2.isEnabled()).isFalse();
        assertThat(kcAfterWorker2.getAttributes()).containsEntry("awgment_status", List.of("DISABLED"));
        assertThat(kcAfterWorker2.getAttributes()).containsEntry("awgment_sync_version", List.of("2"));

        // 4. Worker 1 wakes up and attempts stale v1 Keycloak write + stale lease_token CAS completion
        ReconcileResult staleKcAttempt = keycloakUserSyncClient.reconcileUser("acme", worker1StalePayload);
        assertThat(staleKcAttempt.fenced()).isTrue();

        boolean staleCasUpdated = outboxStore.completeWithCas(
                job1.event().getId(), worker1LeaseToken, userId, 1L, kcSub, false);
        assertThat(staleCasUpdated).isFalse();

        // Keycloak & PG remain cleanly at DISABLED v2
        UserRepresentation kcAfterFencedWorker1 = kcAdmin.realm("acme").users().get(kcSub).toRepresentation();
        assertThat(kcAfterFencedWorker1.isEnabled()).isFalse();
        assertThat(kcAfterFencedWorker1.getAttributes()).containsEntry("awgment_status", List.of("DISABLED"));
        assertThat(kcAfterFencedWorker1.getAttributes()).containsEntry("awgment_sync_version", List.of("2"));

        // 5. Simulate worst-case late stale write reaching KC before Worker 2's version stamp -> healed by 15s verification pass!
        kcAfterFencedWorker1.setEnabled(true);
        Map<String, List<String>> corruptedAttrs = new HashMap<>(kcAfterFencedWorker1.getAttributes());
        corruptedAttrs.put("awgment_status", List.of("ACTIVE"));
        corruptedAttrs.put("awgment_sync_version", List.of("1"));
        kcAfterFencedWorker1.setAttributes(corruptedAttrs);
        kcAdmin.realm("acme").users().get(kcSub).update(kcAfterFencedWorker1);

        // Before 15s delay elapses, verification pass must NOT run prematurely
        assertThat(worker.processTenantQueue("acme", 20)).isZero();

        // Advance past 15s verification delay -> verification pass heals live Keycloak to DISABLED v2
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox SET verification_required_at = NOW() - INTERVAL '1 second' WHERE id = ?")) {
            ps.setLong(1, job1.event().getId());
            ps.executeUpdate();
        }
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(1);

        UserRepresentation healedKcUser = kcAdmin.realm("acme").users().get(kcSub).toRepresentation();
        assertThat(healedKcUser.isEnabled()).isFalse();
        assertThat(healedKcUser.getAttributes()).containsEntry("awgment_status", List.of("DISABLED"));
        assertThat(healedKcUser.getAttributes()).containsEntry("awgment_sync_version", List.of("2"));

        TenantContext.setTenantId("acme");
        KeycloakOutboxEventEntity finalOutbox = outboxRepository.findById(job1.event().getId()).orElseThrow();
        assertThat(finalOutbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(finalOutbox.getVerificationRequiredAt()).isNull();
    }

    @Test
    @Order(2)
    void shouldEnforceSkipLockedConcurrencyActionEmailGatingAndSyncOutboxRetryApi() throws Exception {
        TenantContext.setTenantId("acme");

        // Create 4 independent users for concurrent FOR UPDATE SKIP LOCKED claim test
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long uid = idGenerator.nextId().longValue();
            UserEntity u = new UserEntity();
            u.setId(uid);
            u.setUserName("skip_lock_" + i);
            u.setEmailId("skip_lock_" + i + "@" + "acme.invalid");
            u.setFirstName("Skip");
            u.setLastName("Lock" + i);
            u.setLocale("en");
            u.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
            u.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
            u.setStatus(UserStatus.ACTIVE);
            u.setSyncVersion(1L);
            u.setSyncedVersion(0L);
            u.setSyncStatus(SyncStatus.PENDING_SYNC);
            userRepository.saveAndFlush(u);
            outboxStore.upsertUserReconcile(uid, 1L);
            userIds.add(uid);
        }

        // 4 concurrent threads calling claimNextJobs(1, ...) must claim disjoint outbox IDs
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<List<ClaimedOutboxJob>>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                final String wId = "concurrent-w-" + t;
                futures.add(pool.submit((Callable<List<ClaimedOutboxJob>>) () -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    TenantContext.setTenantId("acme");
                    try {
                        return outboxStore.claimNextJobs(1, Duration.ofSeconds(60), wId);
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Set<Long> claimedIds = new HashSet<>();
            for (Future<List<ClaimedOutboxJob>> f : futures) {
                List<ClaimedOutboxJob> list = f.get(15, TimeUnit.SECONDS);
                assertThat(list).hasSize(1);
                claimedIds.add(list.getFirst().event().getId());
            }
            assertThat(claimedIds).hasSize(4);
        } finally {
            pool.shutdownNow();
        }

        // Verify SEND_ACTION_EMAIL waits while keycloakSub is null and cancels when user becomes DISABLED
        long gatedUserId = userIds.getFirst();
        long emailId = outboxStore.enqueueActionEmail(gatedUserId, List.of("UPDATE_PASSWORD"), 3600);
        // Force claim & process email while keycloakSub is still null
        TenantContext.setTenantId("acme");
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(1);
        KeycloakOutboxEventEntity deferredEmail = outboxRepository.findById(emailId).orElseThrow();
        assertThat(deferredEmail.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(deferredEmail.getRetryCount()).isZero();

        UserEntity disableTarget = userRepository.findById(gatedUserId).orElseThrow();
        disableTarget.setStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(disableTarget);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox SET next_retry_at = NOW() - INTERVAL '1 second' WHERE id = ?")) {
            ps.setLong(1, emailId);
            ps.executeUpdate();
        }
        assertThat(worker.processTenantQueue("acme", 20)).isEqualTo(1);
        assertThat(outboxRepository.findById(emailId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.CANCELLED);

        // Verify UC-SYNC-01 & UC-SYNC-02 HTTP endpoints against real PostgreSQL & Keycloak
        long retryUserId = userIds.get(1);
        KeycloakOutboxEventEntity retryOutbox = outboxRepository
                .findByAggregateTypeAndAggregateIdAndJobType(
                        OutboxAggregateType.USER, retryUserId, OutboxJobType.RECONCILE_USER)
                .orElseThrow();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox "
                             + "SET status = 'FAILED', retry_count = 5, lease_token = NULL, locked_at = NULL, last_error = 'Simulated failure' "
                             + "WHERE id = ?")) {
            ps.setLong(1, retryOutbox.getId());
            ps.executeUpdate();
        }

        String acmeOperatorToken = fetchToken("acme", Map.of(
                "grant_type", "password",
                "client_id", "awgment-web",
                "client_secret", "awgment-web-dev-secret",
                "username", "acme-operator",
                "password", "OperatorDev!"
        ));

        HttpResponse<String> listFailed = sendApi(
                "GET", "/accounts/v2/sync/outbox?status=FAILED&aggregateType=USER", acmeOperatorToken);
        assertThat(listFailed.statusCode()).isEqualTo(200);
        JsonNode listJson = OBJECT_MAPPER.readTree(listFailed.body());
        assertThat(listJson.path("content").toString()).contains(String.valueOf(retryOutbox.getId()));

        HttpResponse<String> retryResp = sendApi(
                "POST", "/accounts/v2/sync/outbox/" + retryOutbox.getId() + "/retry", acmeOperatorToken);
        assertThat(retryResp.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(retryResp.body()).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(userRepository.findById(retryUserId).orElseThrow().getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        // Clear any leftover IN_PROGRESS rows from the SKIP LOCKED test so DB is clean
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE tenant_acme.tp_keycloak_outbox SET status = 'COMPLETED', lease_token = NULL, locked_at = NULL WHERE status = 'IN_PROGRESS'");
        }
    }

    @Test
    @Order(3)
    void shouldRecoverDirtyAtMaxRetriesPreserveReclaimedVerificationAndHaveV3Indexes() throws Exception {
        // 1. Verify Flyway V3 columns and search/SLA indexes exist on tenant_acme
        List<String> expectedIndexes = List.of(
                "idx_tp_user_user_name_lower_pattern",
                "idx_tp_user_email_id_lower_pattern",
                "idx_tp_user_first_name_lower_pattern",
                "idx_tp_user_last_name_lower_pattern",
                "idx_tp_user_created_at_desc",
                "idx_tp_keycloak_outbox_pending_since"
        );
        Set<String> foundIndexes = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT indexname FROM pg_indexes WHERE schemaname = 'tenant_acme'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                foundIndexes.add(rs.getString(1));
            }
        }
        assertThat(foundIndexes).containsAll(expectedIndexes);

        // 2. dirty = TRUE at retry_count = maxRetries - 1 re-queues PENDING (retry_count = 0) and recovers cleanly
        TenantContext.setTenantId("acme");
        long maxRetryUserId = idGenerator.nextId().longValue();
        UserEntity maxRetryUser = new UserEntity();
        maxRetryUser.setId(maxRetryUserId);
        maxRetryUser.setUserName("dirty.maxretry");
        maxRetryUser.setEmailId("dirty.maxretry" + "@" + "acme.invalid");
        maxRetryUser.setFirstName("Dirty");
        maxRetryUser.setLastName("MaxRetry");
        maxRetryUser.setLocale("en");
        maxRetryUser.setIdentitySource(IdentitySource.KEYCLOAK_LOCAL);
        maxRetryUser.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        maxRetryUser.setStatus(UserStatus.ACTIVE);
        maxRetryUser.setDesiredRoles(List.of("awgment-user"));
        maxRetryUser.setSyncVersion(1L);
        maxRetryUser.setSyncedVersion(0L);
        maxRetryUser.setSyncStatus(SyncStatus.PENDING_SYNC);
        maxRetryUser.setCreatedBy("op-it");
        maxRetryUser.setUpdatedBy("op-it");
        userRepository.saveAndFlush(maxRetryUser);

        outboxStore.upsertUserReconcile(maxRetryUserId, 1L);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox SET retry_count = 4 "
                             + "WHERE aggregate_type = 'USER' AND aggregate_id = ? AND job_type = 'RECONCILE_USER'")) {
            ps.setLong(1, maxRetryUserId);
            ps.executeUpdate();
        }
        ClaimedOutboxJob maxRetryClaim = outboxStore.claimSpecificUserReconcileJob(
                maxRetryUserId, Duration.ofSeconds(60), "it-worker-a").orElseThrow();

        // Simulate newer DB edit while in-flight before failure at retry_count = 4
        maxRetryUser.setFirstName("DirtyUpdated");
        maxRetryUser.setSyncVersion(2L);
        userRepository.saveAndFlush(maxRetryUser);
        outboxStore.upsertUserReconcile(maxRetryUserId, 2L);

        assertThat(outboxStore.markRetryOrFailedWithCas(
                maxRetryClaim.event().getId(),
                maxRetryClaim.event().getLeaseToken(),
                maxRetryUserId,
                OutboxAggregateType.USER,
                OutboxJobType.RECONCILE_USER,
                "Transient KC timeout at retry 4",
                false)).isTrue();

        KeycloakOutboxEventEntity afterDirtyMaxRetry = outboxRepository.findById(maxRetryClaim.event().getId())
                .orElseThrow();
        assertThat(afterDirtyMaxRetry.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterDirtyMaxRetry.getRetryCount()).isZero();
        assertThat(afterDirtyMaxRetry.isDirty()).isFalse();
        assertThat(afterDirtyMaxRetry.getLastError()).isNull();
        assertThat(userRepository.findById(maxRetryUserId).orElseThrow().getSyncStatus())
                .isNotEqualTo(SyncStatus.SYNC_FAILED);

        assertThat(worker.processTenantQueue("acme", 20)).isGreaterThanOrEqualTo(1);
        assertThat(userRepository.findById(maxRetryUserId).orElseThrow().getSyncStatus())
                .isEqualTo(SyncStatus.SYNCED);

        // 3. Reclaimed lease hitting dirty = TRUE preserves reclaimed_verification_pending and heals via 15s pass
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox "
                             + "SET status = 'IN_PROGRESS', lease_token = gen_random_uuid(), "
                             + "    locked_at = NOW() - INTERVAL '300 seconds', verification_required_at = NULL "
                             + "WHERE id = ?")) {
            ps.setLong(1, afterDirtyMaxRetry.getId());
            ps.executeUpdate();
        }

        ClaimedOutboxJob reclaimedClaim = outboxStore.claimSpecificUserReconcileJob(
                maxRetryUserId, Duration.ofSeconds(60), "it-worker-b").orElseThrow();
        assertThat(reclaimedClaim.wasReclaimed()).isTrue();
        assertThat(reclaimedClaim.event().isReclaimedVerificationPending()).isTrue();

        // Concurrent mutation marks dirty = TRUE before reclaimed worker finishes v2
        maxRetryUser = userRepository.findById(maxRetryUserId).orElseThrow();
        maxRetryUser.setFirstName("ReclaimFollowUp");
        maxRetryUser.setSyncVersion(3L);
        userRepository.saveAndFlush(maxRetryUser);
        outboxStore.upsertUserReconcile(maxRetryUserId, 3L);

        assertThat(outboxStore.completeReconcileWithCas(
                reclaimedClaim.event().getId(),
                reclaimedClaim.event().getLeaseToken(),
                maxRetryUserId,
                OutboxAggregateType.USER,
                2L,
                maxRetryUser.getKeycloakSub(),
                true,
                false).reQueuedBecauseDirty()).isTrue();

        KeycloakOutboxEventEntity carried = outboxRepository.findById(reclaimedClaim.event().getId()).orElseThrow();
        assertThat(carried.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(carried.isReclaimedVerificationPending()).isTrue();
        assertThat(carried.getVerificationRequiredAt()).isNull();

        // Next pass is NOT a reclaim, yet schedules forced verification because reclaimed_verification_pending=true
        assertThat(worker.processTenantQueue("acme", 20)).isGreaterThanOrEqualTo(1);
        KeycloakOutboxEventEntity scheduledVerify = outboxRepository.findById(carried.getId()).orElseThrow();
        assertThat(scheduledVerify.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(scheduledVerify.getVerificationRequiredAt()).isNotNull();
        assertThat(scheduledVerify.isReclaimedVerificationPending()).isFalse();

        // Out-of-band Keycloak drift gets healed when the 15s verification pass matures
        var kcUsers = KEYCLOAK.getKeycloakAdminClient().realm("acme").users();
        String kcSub = userRepository.findById(maxRetryUserId).orElseThrow().getKeycloakSub();
        UserRepresentation drifted = kcUsers.get(kcSub).toRepresentation();
        drifted.setFirstName("DriftedInKc");
        kcUsers.get(kcSub).update(drifted);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tenant_acme.tp_keycloak_outbox "
                             + "SET verification_required_at = NOW() - INTERVAL '1 second' WHERE id = ?")) {
            ps.setLong(1, carried.getId());
            ps.executeUpdate();
        }
        assertThat(worker.processTenantQueue("acme", 20)).isGreaterThanOrEqualTo(1);
        assertThat(kcUsers.get(kcSub).toRepresentation().getFirstName()).isEqualTo("ReclaimFollowUp");
        assertThat(outboxRepository.findById(carried.getId()).orElseThrow().getVerificationRequiredAt()).isNull();
    }

    private HttpResponse<String> sendApi(String method, String path, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String fetchToken(String realm, Map<String, String> formParams) throws Exception {
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String> entry : formParams.entrySet()) {
            if (!form.isEmpty()) {
                form.append('&');
            }
            form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body()).path("access_token").asText();
    }
}
