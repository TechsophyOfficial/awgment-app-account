package com.techsophy.tsf.account.idp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tenant-scoped trusted enterprise identity provider pin stored in {@code tp_trusted_idp}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tp_trusted_idp")
public class TrustedIdpEntity {

    private static final String DEFAULT_TENANT_CLAIM_NAME = "idp_tenant_id";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "idp_alias", nullable = false, unique = true, length = 100)
    private String idpAlias;

    @Column(name = "external_tenant_id", length = 128)
    private String externalTenantId;

    @Column(name = "tenant_claim_name", nullable = false, length = 64)
    private String tenantClaimName = DEFAULT_TENANT_CLAIM_NAME;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Version
    @Column(name = "opt_lock_version", nullable = false)
    private Long optLockVersion;

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.tenantClaimName == null || this.tenantClaimName.isBlank()) {
            this.tenantClaimName = DEFAULT_TENANT_CLAIM_NAME;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
