package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.governance.RoleCompositeExpander;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Outbox and governance adapter boundary for Keycloak 26 user reconciliation,
 * bounded synchronous session revocation, composite role expansion, and action emails.
 */
public interface KeycloakUserSyncClient extends RoleCompositeExpander {

    ReconcileResult reconcileUser(String realm, UserReconcilePayload payload);

    boolean revokeUserSynchronously(String realm, String keycloakSub, long syncVersion, String statusName);

    default RevocationAttemptOutcome revokeUserSynchronouslyWithOutcome(
            String realm, String keycloakSub, long syncVersion, String statusName) {
        return revokeUserSynchronously(realm, keycloakSub, syncVersion, statusName)
                ? RevocationAttemptOutcome.REVOKED
                : RevocationAttemptOutcome.DEFERRED_UNCERTAIN;
    }

    @Override
    Set<String> expandCompositeClosure(String tenantRealm, Collection<String> directRoles);

    Set<String> fetchUserEffectiveRoles(String tenantRealm, String keycloakSub);

    void sendActionEmail(String realm, String keycloakSub, List<String> actions, int lifespanSeconds);
}
