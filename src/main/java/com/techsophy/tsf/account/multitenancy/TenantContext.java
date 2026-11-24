package com.techsophy.tsf.account.multitenancy;

import com.techsophy.tsf.account.tenant.TenantIdValidator;

/**
 * Thread-local carrier for the active request/job tenant identifier.
 * Always defaults to {@link #DEFAULT_TENANT} ("public") and must be cleared in a {@code finally} block.
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT = "public";
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || DEFAULT_TENANT.equals(tenantId)) {
            CURRENT_TENANT.remove();
            return;
        }
        TenantIdValidator.validate(tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    public static String getCurrentSchema() {
        String tenantId = getTenantId();
        return DEFAULT_TENANT.equals(tenantId)
                ? DEFAULT_TENANT
                : TenantIdValidator.toSchemaName(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
