package com.techsophy.tsf.account.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link SecurityAuditLogEntity} records in the active schema.
 */
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLogEntity, Long> {

    List<SecurityAuditLogEntity> findByTargetTypeAndTargetIdOrderByCreatedOnDesc(String targetType, String targetId);
}
