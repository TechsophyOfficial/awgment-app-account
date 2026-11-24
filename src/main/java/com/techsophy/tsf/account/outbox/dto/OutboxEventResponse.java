package com.techsophy.tsf.account.outbox.dto;

import com.techsophy.tsf.account.outbox.KeycloakOutboxEventEntity;
import com.techsophy.tsf.account.outbox.OutboxAggregateType;
import com.techsophy.tsf.account.outbox.OutboxJobType;
import com.techsophy.tsf.account.outbox.OutboxStatus;

import java.time.Instant;

/**
 * Sanitized read representation of a tenant outbox row (omitting internal lease tokens).
 */
public record OutboxEventResponse(
        Long id,
        OutboxJobType jobType,
        OutboxAggregateType aggregateType,
        Long aggregateId,
        Long targetVersion,
        boolean dirty,
        OutboxStatus status,
        boolean ambiguousDelivery,
        int retryCount,
        Instant nextRetryAt,
        Instant verificationRequiredAt,
        Instant dispatchedAt,
        String lastError,
        Instant createdOn,
        Instant updatedOn
) {

    public static OutboxEventResponse from(KeycloakOutboxEventEntity entity) {
        return new OutboxEventResponse(
                entity.getId(),
                entity.getJobType(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getTargetVersion(),
                entity.isDirty(),
                entity.getStatus(),
                entity.isAmbiguousDelivery(),
                entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getVerificationRequiredAt(),
                entity.getDispatchedAt(),
                entity.getLastError(),
                entity.getCreatedOn(),
                entity.getUpdatedOn()
        );
    }
}
