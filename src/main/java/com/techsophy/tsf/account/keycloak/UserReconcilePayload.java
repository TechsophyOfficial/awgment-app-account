package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserStatus;

import java.util.List;
import java.util.Objects;

/**
 * Immutable desired-state payload passed from the coalesced outbox worker to Keycloak 26.
 */
public record UserReconcilePayload(
        long userId,
        String keycloakSub,
        String userName,
        String emailId,
        String firstName,
        String lastName,
        String locale,
        UserStatus status,
        IdentitySource identitySource,
        List<String> desiredRoles,
        long syncVersion
) {

    public UserReconcilePayload {
        desiredRoles = desiredRoles == null ? null : List.copyOf(desiredRoles);
    }

    public static UserReconcilePayload from(UserEntity user) {
        Objects.requireNonNull(user, "user must not be null");
        return new UserReconcilePayload(
                user.getId(),
                user.getKeycloakSub(),
                user.getUserName(),
                user.getEmailId(),
                user.getFirstName(),
                user.getLastName(),
                user.getLocale(),
                user.getStatus(),
                user.getIdentitySource(),
                user.getDesiredRoles(),
                user.getSyncVersion() != null ? user.getSyncVersion() : 1L
        );
    }
}
