package com.techsophy.tsf.account.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema-agnostic tenant user profile stored in {@code tenant_<id>.tp_user}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tp_user")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "keycloak_sub", length = 64)
    private String keycloakSub;

    @Column(name = "provisioning_key", length = 128)
    private String provisioningKey;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "email_id", nullable = false, length = 255)
    private String emailId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "mobile_number", length = 32)
    private String mobileNumber;

    @Column(name = "department", length = 150)
    private String department;

    @Column(name = "locale", nullable = false, length = 35)
    private String locale = "en";

    @Column(name = "timezone", length = 64)
    private String timezone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_service_preferences", nullable = false)
    private Map<String, Object> selfServicePreferences = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_source", nullable = false, length = 32)
    private IdentitySource identitySource = IdentitySource.KEYCLOAK_LOCAL;

    @Column(name = "idp_alias", length = 100)
    private String idpAlias;

    @Column(name = "email_verified_at_staging", nullable = false)
    private boolean emailVerifiedAtStaging = false;

    @Column(name = "idp_verified_at_staging", nullable = false)
    private boolean idpVerifiedAtStaging = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "provisioning_mode", nullable = false, length = 32)
    private ProvisioningMode provisioningMode = ProvisioningMode.DIRECT_OPERATOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_roles")
    private List<String> desiredRoles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_groups")
    private List<String> desiredGroups;

    @Column(name = "sync_version", nullable = false)
    private Long syncVersion = 1L;

    @Column(name = "synced_version", nullable = false)
    private Long syncedVersion = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private SyncStatus syncStatus = SyncStatus.PENDING_SYNC;

    @Version
    @Column(name = "opt_lock_version", nullable = false)
    private Long optLockVersion;

    @Column(name = "approved_by_id")
    private Long approvedById;

    @Column(name = "approved_on")
    private Instant approvedOn;

    @Column(name = "approval_attestation", length = 500)
    private String approvalAttestation;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "last_action_email_at")
    private Instant lastActionEmailAt;

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
        if (this.locale == null || this.locale.isBlank()) {
            this.locale = "en";
        }
        if (this.selfServicePreferences == null) {
            this.selfServicePreferences = new LinkedHashMap<>();
        }
        if (this.attributes == null) {
            this.attributes = new LinkedHashMap<>();
        }
        if (this.identitySource == null) {
            this.identitySource = IdentitySource.KEYCLOAK_LOCAL;
        }
        if (this.provisioningMode == null) {
            this.provisioningMode = ProvisioningMode.DIRECT_OPERATOR;
        }
        if (this.syncVersion == null) {
            this.syncVersion = 1L;
        }
        if (this.syncedVersion == null) {
            this.syncedVersion = 0L;
        }
        if (this.syncStatus == null) {
            this.syncStatus = SyncStatus.PENDING_SYNC;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
