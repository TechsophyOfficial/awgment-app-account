CREATE TABLE IF NOT EXISTS public.tp_tenant
(
    id               BIGINT PRIMARY KEY,
    tenant_id        VARCHAR(50)  NOT NULL UNIQUE,
    schema_name      VARCHAR(63)  NOT NULL UNIQUE,
    display_name     VARCHAR(150) NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    last_error       VARCHAR(2000),
    opt_lock_version BIGINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tp_tenant_tenant_id_format
        CHECK (tenant_id ~ '^[a-z][a-z0-9_]{1,49}$'),
    CONSTRAINT chk_tp_tenant_reserved_id
        CHECK (tenant_id NOT IN ('public', 'master', 'platform', 'information_schema', 'admin', 'system')
            AND tenant_id !~ '^pg_'),
    CONSTRAINT chk_tp_tenant_schema_name_match
        CHECK (schema_name = 'tenant_' || tenant_id),
    CONSTRAINT chk_tp_tenant_status
        CHECK (status IN ('PROVISIONING', 'PROVISIONING_FAILED', 'ACTIVE', 'SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_tp_tenant_status
    ON public.tp_tenant (status);

CREATE INDEX IF NOT EXISTS idx_tp_tenant_created_at
    ON public.tp_tenant (created_at DESC);

CREATE TABLE IF NOT EXISTS public.tp_security_audit_log
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
    CONSTRAINT chk_public_audit_outcome
        CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_public_audit_created_on
    ON public.tp_security_audit_log (created_on DESC);

CREATE INDEX IF NOT EXISTS idx_public_audit_target
    ON public.tp_security_audit_log (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_public_audit_action
    ON public.tp_security_audit_log (action);
