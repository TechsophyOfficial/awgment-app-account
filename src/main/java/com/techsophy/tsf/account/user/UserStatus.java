package com.techsophy.tsf.account.user;

/**
 * Lifecycle status of a tenant user stored in {@code tp_user}.
 */
public enum UserStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    DISABLED
}
