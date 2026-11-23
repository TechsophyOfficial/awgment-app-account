package com.techsophy.tsf.account.tenant;

/**
 * Lifecycle states for a registered tenant in {@code public.tp_tenant}.
 */
public enum TenantStatus {
    PROVISIONING,
    PROVISIONING_FAILED,
    ACTIVE,
    SUSPENDED
}
