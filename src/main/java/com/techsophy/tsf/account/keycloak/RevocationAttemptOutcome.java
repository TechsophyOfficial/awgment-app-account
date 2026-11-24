package com.techsophy.tsf.account.keycloak;

/**
 * Detailed outcome of a bounded synchronous Keycloak user revocation attempt.
 */
public enum RevocationAttemptOutcome {
    REVOKED,
    FENCED,
    DEFERRED_BEFORE_WRITE,
    DEFERRED_UNCERTAIN
}
