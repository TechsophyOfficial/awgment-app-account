package com.techsophy.tsf.account.user;

import java.util.Set;

/**
 * Immutable representation of the authenticated caller resolved from Spring Security context.
 */
public record CallerPrincipal(
        String subject,
        String tenantRealm,
        Set<String> directRoles,
        boolean verifiedServiceAccount
) {

    public CallerPrincipal {
        directRoles = directRoles == null ? Set.of() : Set.copyOf(directRoles);
    }
}
