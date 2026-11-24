package com.techsophy.tsf.account.user;

/**
 * Outbox reconciliation status between PostgreSQL and Keycloak 26.
 */
public enum SyncStatus {
    SYNCED,
    PENDING_SYNC,
    SYNC_FAILED
}
