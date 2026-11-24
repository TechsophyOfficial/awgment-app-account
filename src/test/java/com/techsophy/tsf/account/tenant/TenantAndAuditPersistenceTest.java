package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditLogEntity;
import com.techsophy.tsf.account.audit.SecurityAuditLogRepository;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.config.IdGeneratorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({IdGeneratorConfig.class, SecurityAuditService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class TenantAndAuditPersistenceTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SecurityAuditLogRepository auditLogRepository;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private IdGeneratorImpl idGenerator;

    @Test
    void shouldPersistTenantWithSnowflakeIdOptimisticLockingAndRepositoryQueries() {
        String tenantId = "persist_acme";
        TenantEntity entity = new TenantEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setTenantId(tenantId);
        entity.setSchemaName(TenantIdValidator.toSchemaName(tenantId));
        entity.setDisplayName("Persist Acme");
        entity.setStatus(TenantStatus.PROVISIONING);
        entity.setCreatedBy("platform-admin");
        entity.setUpdatedBy("platform-admin");

        TenantEntity saved = tenantRepository.saveAndFlush(entity);
        assertThat(saved.getOptLockVersion()).isEqualTo(0L);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        assertThat(tenantRepository.existsByTenantId(tenantId)).isTrue();
        assertThat(tenantRepository.findByTenantId(tenantId)).isPresent();
        List<TenantEntity> provisioning = tenantRepository.findByStatusOrderByTenantIdAsc(TenantStatus.PROVISIONING);
        assertThat(provisioning).extracting(TenantEntity::getTenantId).contains(tenantId);

        Page<TenantEntity> page = tenantRepository.findByStatus(TenantStatus.PROVISIONING, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(TenantEntity::getTenantId).contains(tenantId);

        // Stale copy triggers OptimisticLockingFailureException
        TenantEntity staleCopy = tenantRepository.findByTenantId(tenantId).orElseThrow();
        saved.setStatus(TenantStatus.ACTIVE);
        saved.setUpdatedBy("platform-admin");
        tenantRepository.saveAndFlush(saved);

        staleCopy.setStatus(TenantStatus.SUSPENDED);
        assertThatThrownBy(() -> tenantRepository.saveAndFlush(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Explicit timestamps on persist keep existing values
        TenantEntity explicitTimestamps = new TenantEntity();
        explicitTimestamps.setId(idGenerator.nextId().longValue());
        explicitTimestamps.setTenantId("persist_beta");
        explicitTimestamps.setSchemaName(TenantIdValidator.toSchemaName("persist_beta"));
        explicitTimestamps.setDisplayName("Persist Beta");
        explicitTimestamps.setStatus(TenantStatus.ACTIVE);
        java.time.Instant fixed = java.time.Instant.parse("2026-01-01T00:00:00Z");
        explicitTimestamps.setCreatedAt(fixed);
        explicitTimestamps.setUpdatedAt(fixed);
        TenantEntity savedExplicit = tenantRepository.saveAndFlush(explicitTimestamps);
        assertThat(savedExplicit.getCreatedAt()).isEqualTo(fixed);

        tenantRepository.deleteAll();
    }

    @Test
    void shouldPersistSecurityAuditLogEntityViaSecurityAuditService() {
        SecurityAuditLogEntity saved = securityAuditService.record(
                "TENANT_CREATE",
                "TENANT",
                "acme",
                AuditOutcome.SUCCESS,
                "platform-admin",
                501L,
                Map.of("schemaName", "tenant_acme", "token", "must-be-stripped")
        );

        SecurityAuditLogEntity reloaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAction()).isEqualTo("TENANT_CREATE");
        assertThat(reloaded.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(reloaded.getMetadata())
                .containsEntry("schemaName", "tenant_acme")
                .doesNotContainKey("token");

        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedOnDesc("TENANT", "acme"))
                .hasSize(1);

        // Direct save with null createdOn & metadata tests @PrePersist defaults
        SecurityAuditLogEntity minimal = new SecurityAuditLogEntity();
        minimal.setId(idGenerator.nextId().longValue());
        minimal.setAction("PING");
        minimal.setTargetType("SYSTEM");
        minimal.setOutcome(AuditOutcome.SUCCESS);
        minimal.setMetadata(null);
        SecurityAuditLogEntity savedMinimal = auditLogRepository.saveAndFlush(minimal);
        assertThat(savedMinimal.getCreatedOn()).isNotNull();
        assertThat(savedMinimal.getMetadata()).isNotNull().isEmpty();

        auditLogRepository.deleteAll();
    }
}
