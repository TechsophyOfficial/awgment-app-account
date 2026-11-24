package com.techsophy.tsf.account.user;

/**
 * Mechanism by which a tenant user record was initially provisioned.
 */
public enum ProvisioningMode {
    JIT_AUTO_APPROVED,
    JIT_ADMIN_APPROVED,
    JIT_PENDING,
    DIRECT_OPERATOR,
    BULK_IMPORT
}
