package com.techsophy.tsf.account.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Tenant-scoped repository for {@link KeycloakOutboxEventEntity} rows in {@code tp_keycloak_outbox}.
 */
public interface KeycloakOutboxRepository
        extends JpaRepository<KeycloakOutboxEventEntity, Long>, JpaSpecificationExecutor<KeycloakOutboxEventEntity> {

    Optional<KeycloakOutboxEventEntity> findByAggregateTypeAndAggregateIdAndJobType(
            OutboxAggregateType aggregateType,
            Long aggregateId,
            OutboxJobType jobType);

    Page<KeycloakOutboxEventEntity> findByStatus(OutboxStatus status, Pageable pageable);

    Page<KeycloakOutboxEventEntity> findByAggregateType(OutboxAggregateType aggregateType, Pageable pageable);

    Page<KeycloakOutboxEventEntity> findByStatusAndAggregateType(
            OutboxStatus status,
            OutboxAggregateType aggregateType,
            Pageable pageable);
}
