ALTER TABLE public.tp_tenant
    ADD COLUMN IF NOT EXISTS pending_admin_email      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pending_admin_first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pending_admin_last_name  VARCHAR(100);
