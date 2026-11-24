ALTER TABLE tp_keycloak_outbox
    ADD COLUMN IF NOT EXISTS reclaimed_verification_pending BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tp_keycloak_outbox
    ADD COLUMN IF NOT EXISTS pending_since TIMESTAMPTZ;

UPDATE tp_keycloak_outbox
SET pending_since = COALESCE(pending_since, created_on)
WHERE status IN ('PENDING', 'IN_PROGRESS')
  AND job_type IN ('RECONCILE_USER', 'RECONCILE_GROUP')
  AND pending_since IS NULL;

CREATE INDEX IF NOT EXISTS idx_tp_keycloak_outbox_pending_since
    ON tp_keycloak_outbox (pending_since)
    WHERE pending_since IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tp_user_user_name_lower_pattern
    ON tp_user (LOWER(user_name) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_tp_user_email_id_lower_pattern
    ON tp_user (LOWER(email_id) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_tp_user_first_name_lower_pattern
    ON tp_user (LOWER(first_name) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_tp_user_last_name_lower_pattern
    ON tp_user (LOWER(last_name) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_tp_user_created_at_desc
    ON tp_user (created_at DESC);
