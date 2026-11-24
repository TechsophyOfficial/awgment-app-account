package com.techsophy.tsf.account.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schema-agnostic security audit entry stored in {@code tp_security_audit_log}
 * within whichever PostgreSQL schema ({@code public} or {@code tenant_<id>}) is active.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tp_security_audit_log")
public class SecurityAuditLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_sub", updatable = false, length = 100)
    private String actorSub;

    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", updatable = false, length = 100)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 16)
    private AuditOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    @PrePersist
    void onCreate() {
        if (this.createdOn == null) {
            this.createdOn = Instant.now();
        }
        if (this.metadata == null) {
            this.metadata = new LinkedHashMap<>();
        }
    }
}
