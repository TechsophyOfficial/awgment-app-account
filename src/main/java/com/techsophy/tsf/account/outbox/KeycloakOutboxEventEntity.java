package com.techsophy.tsf.account.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Schema-agnostic transactional outbox row stored in {@code tenant_<id>.tp_keycloak_outbox}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tp_keycloak_outbox")
public class KeycloakOutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private OutboxJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 32)
    private OutboxAggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "target_version")
    private Long targetVersion;

    @Column(name = "dirty", nullable = false)
    private boolean dirty = false;

    @Column(name = "verification_required_at")
    private Instant verificationRequiredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @Column(name = "ambiguous_delivery", nullable = false)
    private boolean ambiguousDelivery = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    @Column(name = "updated_on", nullable = false)
    private Instant updatedOn;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdOn == null) {
            this.createdOn = now;
        }
        if (this.updatedOn == null) {
            this.updatedOn = now;
        }
        if (this.nextRetryAt == null) {
            this.nextRetryAt = now;
        }
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedOn = Instant.now();
    }
}
