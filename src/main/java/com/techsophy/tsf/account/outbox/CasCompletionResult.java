package com.techsophy.tsf.account.outbox;

/**
 * Result of an atomic CAS completion on {@code tp_keycloak_outbox}.
 */
public record CasCompletionResult(
        boolean casUpdated,
        boolean reQueuedBecauseDirty
) {
}
