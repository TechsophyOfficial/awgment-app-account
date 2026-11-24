package com.techsophy.tsf.account.governance;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Enforces the 4-tier Awgment role governance policy across recursive composite-role closures,
 * target user protection, Tier 2 strict ownership subsets, and self-action blocks.
 */
@Component
public class RoleGovernancePolicy {

    private static final String PLATFORM_ADMIN_ROLE = "awgment-platform-admin";
    private static final String ACCOUNT_ADMIN_ROLE = "awgment-account-admin";
    private static final String BASE_USER_ROLE = "awgment-user";
    private static final String RESERVED_ROLE_PREFIX = "awgment-";

    private static final Set<String> KNOWN_TIER_2_ROLES = Set.of(
            "awgment-account-all",
            "awgment-account-create-or-update",
            "awgment-account-read",
            "awgment-account-delete",
            "awgment-acl-create-or-update",
            "awgment-acl-read",
            "awgment-acl-evaluate-context",
            "awgment-acl-evaluate-delegated",
            "awgment-system-service"
    );

    private final RoleCompositeExpander compositeExpander;

    @Autowired
    public RoleGovernancePolicy(ObjectProvider<RoleCompositeExpander> compositeExpanderProvider) {
        this(compositeExpanderProvider != null ? compositeExpanderProvider.getIfAvailable() : null);
    }

    public RoleGovernancePolicy(RoleCompositeExpander compositeExpander) {
        this.compositeExpander = compositeExpander != null
                ? compositeExpander
                : RoleCompositeExpander.IDENTITY;
    }

    public RoleGovernancePolicy() {
        this(RoleCompositeExpander.IDENTITY);
    }

    /**
     * Cycle-safe {@code O(V + E)} BFS expansion over a composite-role graph.
     */
    public static Set<String> expandClosureBfs(
            Collection<String> directRoles,
            Function<String, Collection<String>> compositeChildrenLookup) {
        if (directRoles == null || directRoles.isEmpty()) {
            return Set.of();
        }
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        enqueueNonBlank(directRoles, visited, queue);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            Collection<String> children = compositeChildrenLookup.apply(current);
            if (children != null && !children.isEmpty()) {
                enqueueNonBlank(children, visited, queue);
            }
        }
        return Set.copyOf(visited);
    }

    public RoleTier classifyRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Role name must not be blank.");
        }
        String normalized = roleName.trim().toLowerCase(Locale.ROOT);
        if (PLATFORM_ADMIN_ROLE.equals(normalized)) {
            return RoleTier.TIER_0_PLATFORM;
        }
        if (ACCOUNT_ADMIN_ROLE.equals(normalized)) {
            return RoleTier.TIER_1_APPROVER;
        }
        if (BASE_USER_ROLE.equals(normalized)) {
            return RoleTier.TIER_3_APP_USER;
        }
        if (KNOWN_TIER_2_ROLES.contains(normalized) || normalized.startsWith(RESERVED_ROLE_PREFIX)) {
            return RoleTier.TIER_2_OPERATOR_ADMIN;
        }
        return RoleTier.TIER_3_APP_USER;
    }

    public Set<String> expandEffectiveRoles(String tenantRealm, Collection<String> directRoles) {
        Set<String> normalized = normalizeRoles(directRoles);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        Set<String> expanded = compositeExpander.expandCompositeClosure(tenantRealm, normalized);
        if (expanded == null || expanded.isEmpty()) {
            return Set.copyOf(normalized);
        }
        Set<String> merged = new LinkedHashSet<>(normalized);
        merged.addAll(normalizeRoles(expanded));
        return Set.copyOf(merged);
    }

    public RoleTier highestTier(Collection<String> effectiveRoles) {
        if (effectiveRoles == null || effectiveRoles.isEmpty()) {
            return RoleTier.TIER_3_APP_USER;
        }
        RoleTier highest = RoleTier.TIER_3_APP_USER;
        for (String role : effectiveRoles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            RoleTier tier = classifyRole(role);
            if (tier.isHigherPrivilegeThan(highest)) {
                highest = tier;
                if (highest == RoleTier.TIER_0_PLATFORM) {
                    break;
                }
            }
        }
        return highest;
    }

    public void assertCanManageTargetUser(
            String tenantRealm,
            Collection<String> callerDirectOrEffectiveRoles,
            Collection<String> targetRoles) {
        Set<String> callerEffective = expandEffectiveRoles(tenantRealm, callerDirectOrEffectiveRoles);
        Set<String> targetEffective = expandEffectiveRoles(tenantRealm, targetRoles);
        assertCanManageTargetExpanded(callerEffective, targetEffective);
    }

    public void assertCanAssignRoles(
            String tenantRealm,
            Collection<String> callerEffectiveRoles,
            Collection<String> targetCurrentRoles,
            Collection<String> desiredRoles) {
        Set<String> callerEffective = expandEffectiveRoles(tenantRealm, callerEffectiveRoles);
        Set<String> currentEffective = expandEffectiveRoles(tenantRealm, targetCurrentRoles);
        Set<String> desiredEffective = expandEffectiveRoles(tenantRealm, desiredRoles);

        assertCanManageTargetExpanded(callerEffective, currentEffective);

        for (String role : desiredEffective) {
            if (!currentEffective.contains(role)) {
                assertRoleMutationAllowed(callerEffective, role, "assign");
            }
        }
        for (String role : currentEffective) {
            if (!desiredEffective.contains(role)) {
                assertRoleMutationAllowed(callerEffective, role, "remove");
            }
        }
    }

    public void assertNotSelfAction(String callerSub, String targetSub, String action) {
        if (callerSub != null && !callerSub.isBlank() && callerSub.equals(targetSub)) {
            String safeAction = (action == null || action.isBlank()) ? "perform this action on" : action.trim();
            throw new ApiException(
                    ApiErrorCode.SELF_ACTION_FORBIDDEN,
                    "Users are not permitted to " + ProblemDetailFactory.sanitizeForLog(safeAction)
                            + " their own account."
            );
        }
    }

    private void assertCanManageTargetExpanded(Set<String> callerEffective, Set<String> targetEffective) {
        RoleTier callerTier = highestTier(callerEffective);
        if (callerTier == RoleTier.TIER_3_APP_USER) {
            throw new ApiException(
                    ApiErrorCode.ROLE_ESCALATION_FORBIDDEN,
                    "Caller lacks administrative governance authority."
            );
        }
        RoleTier targetTier = highestTier(targetEffective);
        if (targetTier.isHigherPrivilegeThan(callerTier)) {
            throw new ApiException(
                    ApiErrorCode.ROLE_ESCALATION_FORBIDDEN,
                    "Cannot manage a target user holding higher-privilege tier " + targetTier + "."
            );
        }
    }

    private void assertRoleMutationAllowed(Set<String> callerEffective, String role, String verb) {
        RoleTier tier = classifyRole(role);
        if (tier == RoleTier.TIER_0_PLATFORM || tier == RoleTier.TIER_1_APPROVER) {
            throw new ApiException(
                    ApiErrorCode.ROLE_ESCALATION_FORBIDDEN,
                    "Cannot " + verb + " protected " + tier + " role '"
                            + ProblemDetailFactory.sanitizeForLog(role) + "'."
            );
        }
        if (tier == RoleTier.TIER_2_OPERATOR_ADMIN && !callerEffective.contains(role)) {
            throw new ApiException(
                    ApiErrorCode.ROLE_ESCALATION_FORBIDDEN,
                    "Caller lacks ownership to " + verb + " Tier 2 role '"
                            + ProblemDetailFactory.sanitizeForLog(role) + "'."
            );
        }
    }

    private static Set<String> normalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                normalized.add(role.trim());
            }
        }
        return normalized;
    }

    private static void enqueueNonBlank(
            Collection<String> roles, Set<String> visited, Deque<String> queue) {
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                String trimmed = role.trim();
                if (visited.add(trimmed)) {
                    queue.add(trimmed);
                }
            }
        }
    }
}
