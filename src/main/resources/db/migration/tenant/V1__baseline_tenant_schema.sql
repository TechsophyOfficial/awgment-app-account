CREATE TABLE IF NOT EXISTS tp_security_audit_log
(
    id          BIGINT PRIMARY KEY,
    actor_id    BIGINT,
    actor_sub   VARCHAR(100),
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(100),
    outcome     VARCHAR(16)  NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_on  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tp_security_audit_log_outcome
        CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_tp_security_audit_log_created_on
    ON tp_security_audit_log (created_on DESC);

CREATE INDEX IF NOT EXISTS idx_tp_security_audit_log_target
    ON tp_security_audit_log (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_tp_security_audit_log_action
    ON tp_security_audit_log (action);

CREATE TABLE IF NOT EXISTS tp_user
(
    id                        BIGINT PRIMARY KEY,
    keycloak_sub              VARCHAR(64),
    provisioning_key          VARCHAR(128),
    user_name                 VARCHAR(100) NOT NULL,
    email_id                  VARCHAR(255) NOT NULL,
    first_name                VARCHAR(100),
    last_name                 VARCHAR(100),
    mobile_number             VARCHAR(32),
    department                VARCHAR(150),
    locale                    VARCHAR(35)  NOT NULL DEFAULT 'en',
    timezone                  VARCHAR(64),
    self_service_preferences  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    attributes                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    identity_source           VARCHAR(32)  NOT NULL DEFAULT 'KEYCLOAK_LOCAL',
    idp_alias                 VARCHAR(100),
    email_verified_at_staging BOOLEAN      NOT NULL DEFAULT FALSE,
    idp_verified_at_staging   BOOLEAN      NOT NULL DEFAULT FALSE,
    provisioning_mode         VARCHAR(32)  NOT NULL DEFAULT 'DIRECT_OPERATOR',
    status                    VARCHAR(32)  NOT NULL,
    desired_roles             JSONB,
    desired_groups            JSONB,
    sync_version              BIGINT       NOT NULL DEFAULT 1,
    synced_version            BIGINT       NOT NULL DEFAULT 0,
    sync_status               VARCHAR(32)  NOT NULL DEFAULT 'PENDING_SYNC',
    opt_lock_version          BIGINT       NOT NULL DEFAULT 0,
    approved_by_id            BIGINT,
    approved_on               TIMESTAMPTZ,
    approval_attestation      VARCHAR(500),
    rejection_reason          VARCHAR(500),
    last_action_email_at      TIMESTAMPTZ,
    created_by                VARCHAR(100),
    updated_by                VARCHAR(100),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tp_user_identity_source
        CHECK (identity_source IN ('KEYCLOAK_LOCAL', 'EXTERNAL_IDP')),
    CONSTRAINT chk_tp_user_provisioning_mode
        CHECK (provisioning_mode IN (
            'JIT_AUTO_APPROVED',
            'JIT_ADMIN_APPROVED',
            'JIT_PENDING',
            'DIRECT_OPERATOR',
            'BULK_IMPORT'
        )),
    CONSTRAINT chk_tp_user_status
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'DISABLED')),
    CONSTRAINT chk_tp_user_sync_status
        CHECK (sync_status IN ('SYNCED', 'PENDING_SYNC', 'SYNC_FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tp_user_keycloak_sub
    ON tp_user (keycloak_sub)
    WHERE keycloak_sub IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tp_user_provisioning_key
    ON tp_user (provisioning_key)
    WHERE provisioning_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tp_user_active_user_name
    ON tp_user (user_name)
    WHERE status IN ('ACTIVE', 'DISABLED');

CREATE UNIQUE INDEX IF NOT EXISTS uq_tp_user_active_email
    ON tp_user (LOWER(email_id))
    WHERE status IN ('ACTIVE', 'DISABLED');

CREATE INDEX IF NOT EXISTS idx_tp_user_attributes_gin
    ON tp_user USING GIN (attributes);

CREATE INDEX IF NOT EXISTS idx_tp_user_status
    ON tp_user (status);

CREATE INDEX IF NOT EXISTS idx_tp_user_department
    ON tp_user (department);

CREATE INDEX IF NOT EXISTS idx_tp_user_identity_source
    ON tp_user (identity_source, idp_alias);
