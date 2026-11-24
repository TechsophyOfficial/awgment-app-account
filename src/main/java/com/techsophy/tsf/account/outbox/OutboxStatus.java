package com.techsophy.tsf.account.outbox;

/**
 * Execution lifecycle states for {@code tp_keycloak_outbox} rows.
 */
public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    DISPATCHED,
    CANCELLED,
    FAILED
}
