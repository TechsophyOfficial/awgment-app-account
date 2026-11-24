CREATE TABLE IF NOT EXISTS tp_trusted_idp
(
    id                 BIGINT PRIMARY KEY,
    idp_alias          VARCHAR(100) NOT NULL UNIQUE,
    external_tenant_id VARCHAR(128),
    tenant_claim_name  VARCHAR(64)  NOT NULL DEFAULT 'idp_tenant_id',
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    opt_lock_version   BIGINT       NOT NULL DEFAULT 0,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tp_trusted_idp_enabled
    ON tp_trusted_idp (enabled);

CREATE TABLE IF NOT EXISTS tp_keycloak_outbox
(
    id                       BIGINT PRIMARY KEY,
    job_type                 VARCHAR(32) NOT NULL,
    aggregate_type           VARCHAR(32) NOT NULL,
    aggregate_id             BIGINT      NOT NULL,
    target_version           BIGINT,
    dirty                    BOOLEAN     NOT NULL DEFAULT FALSE,
    verification_required_at TIMESTAMPTZ,
    payload                  JSONB,
    ambiguous_delivery       BOOLEAN     NOT NULL DEFAULT FALSE,
    status                   VARCHAR(32) NOT NULL,
    lease_token              UUID,
    locked_at                TIMESTAMPTZ,
    locked_by                VARCHAR(100),
    retry_count              INT         NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    dispatched_at            TIMESTAMPTZ,
    last_error               VARCHAR(2000),
    created_on               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tp_keycloak_outbox_job_type
        CHECK (job_type IN ('RECONCILE_USER', 'RECONCILE_GROUP', 'SEND_ACTION_EMAIL')),
    CONSTRAINT chk_tp_keycloak_outbox_aggregate_type
        CHECK (aggregate_type IN ('USER', 'GROUP')),
    CONSTRAINT chk_tp_keycloak_outbox_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'DISPATCHED', 'CANCELLED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tp_keycloak_outbox_coalesced_reconcile
    ON tp_keycloak_outbox (aggregate_type, aggregate_id)
    WHERE job_type IN ('RECONCILE_USER', 'RECONCILE_GROUP');

CREATE INDEX IF NOT EXISTS idx_tp_keycloak_outbox_status_next_retry
    ON tp_keycloak_outbox (status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_tp_keycloak_outbox_verification_required
    ON tp_keycloak_outbox (verification_required_at)
    WHERE verification_required_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tp_keycloak_outbox_locked_in_progress
    ON tp_keycloak_outbox (locked_at)
    WHERE status = 'IN_PROGRESS';
