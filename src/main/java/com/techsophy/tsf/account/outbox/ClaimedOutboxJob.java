package com.techsophy.tsf.account.outbox;

/**
 * Snapshot of a claimed outbox job and whether it was reclaimed from an expired lease.
 */
public record ClaimedOutboxJob(
        KeycloakOutboxEventEntity event,
        boolean wasReclaimed
) {
}
