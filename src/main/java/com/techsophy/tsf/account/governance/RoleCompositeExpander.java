package com.techsophy.tsf.account.governance;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Expands direct role names into their full recursive composite closure for a tenant realm.
 */
@FunctionalInterface
public interface RoleCompositeExpander {

    RoleCompositeExpander IDENTITY = (tenantRealm, directRoles) -> {
        if (directRoles == null || directRoles.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : directRoles) {
            if (role != null && !role.isBlank()) {
                normalized.add(role.trim());
            }
        }
        return Set.copyOf(normalized);
    };

    Set<String> expandCompositeClosure(String tenantRealm, Collection<String> directRoles);
}
