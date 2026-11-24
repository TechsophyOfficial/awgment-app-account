package com.techsophy.tsf.account.outbox;

/**
 * Supported job types persisted in {@code tp_keycloak_outbox}.
 */
public enum OutboxJobType {
    RECONCILE_USER,
    RECONCILE_GROUP,
    SEND_ACTION_EMAIL
}
