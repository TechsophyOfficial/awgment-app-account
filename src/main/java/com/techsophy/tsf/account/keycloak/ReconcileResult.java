package com.techsophy.tsf.account.keycloak;

/**
 * Result of reconciling a user's desired state to Keycloak 26.
 */
public record ReconcileResult(
        String keycloakSub,
        boolean fenced
) {
}
