package com.techsophy.tsf.account.governance;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleGovernancePolicyTest {

    @Test
    void roleTierLevelsAndComparisonShouldFollowStrictPrivilegeHierarchy() {
        assertThat(RoleTier.TIER_0_PLATFORM.level()).isZero();
        assertThat(RoleTier.TIER_1_APPROVER.level()).isEqualTo(1);
        assertThat(RoleTier.TIER_2_OPERATOR_ADMIN.level()).isEqualTo(2);
        assertThat(RoleTier.TIER_3_APP_USER.level()).isEqualTo(3);

        assertThat(RoleTier.TIER_0_PLATFORM.isHigherPrivilegeThan(RoleTier.TIER_1_APPROVER)).isTrue();
        assertThat(RoleTier.TIER_1_APPROVER.isHigherPrivilegeThan(RoleTier.TIER_2_OPERATOR_ADMIN)).isTrue();
        assertThat(RoleTier.TIER_2_OPERATOR_ADMIN.isHigherPrivilegeThan(RoleTier.TIER_3_APP_USER)).isTrue();
        assertThat(RoleTier.TIER_2_OPERATOR_ADMIN.isHigherPrivilegeThan(RoleTier.TIER_2_OPERATOR_ADMIN)).isFalse();
        assertThat(RoleTier.TIER_3_APP_USER.isHigherPrivilegeThan(RoleTier.TIER_1_APPROVER)).isFalse();
    }

    @Test
    void shouldClassifyAllFourTiersDeterministicallyAndFailClosedOnUnknownAwgmentRoles() {
        RoleGovernancePolicy policy = new RoleGovernancePolicy();

        assertThat(policy.classifyRole("awgment-platform-admin")).isEqualTo(RoleTier.TIER_0_PLATFORM);
        assertThat(policy.classifyRole("  AWGMENT-PLATFORM-ADMIN  ")).isEqualTo(RoleTier.TIER_0_PLATFORM);
        assertThat(policy.classifyRole("awgment-account-admin")).isEqualTo(RoleTier.TIER_1_APPROVER);
        assertThat(policy.classifyRole("Awgment-Account-Admin")).isEqualTo(RoleTier.TIER_1_APPROVER);

        for (String tier2Role : List.of(
                "awgment-account-all",
                "awgment-account-create-or-update",
                "awgment-account-read",
                "awgment-account-delete",
                "awgment-acl-create-or-update",
                "awgment-acl-read",
                "awgment-acl-evaluate-context",
                "awgment-acl-evaluate-delegated",
                "awgment-system-service",
                "awgment-unknown-privileged-role",
                "AWGMENT-FUTURE-ADMIN"
        )) {
            assertThat(policy.classifyRole(tier2Role))
                    .as("Expected Tier 2 classification for %s", tier2Role)
                    .isEqualTo(RoleTier.TIER_2_OPERATOR_ADMIN);
        }

        for (String tier3Role : List.of(
                "awgment-user",
                "AWGMENT-USER",
                "editor",
                "viewer",
                "default-roles-acme",
                "offline_access"
        )) {
            assertThat(policy.classifyRole(tier3Role))
                    .as("Expected Tier 3 classification for %s", tier3Role)
                    .isEqualTo(RoleTier.TIER_3_APP_USER);
        }

        assertThatThrownBy(() -> policy.classifyRole(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.classifyRole("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldExpandCompositeClosureSafelyIncludingCyclesAndNulls() {
        Map<String, List<String>> graph = Map.of(
                "team-lead", Arrays.asList("senior-editor", " ", null),
                "senior-editor", List.of("editor", "cycle-a"),
                "cycle-a", List.of("cycle-b", "team-lead"),
                "cycle-b", List.of("cycle-a", "viewer")
        );

        Set<String> expanded = RoleGovernancePolicy.expandClosureBfs(
                Arrays.asList("team-lead", null, "  "),
                role -> graph.getOrDefault(role, List.of())
        );

        assertThat(expanded).containsExactlyInAnyOrder(
                "team-lead", "senior-editor", "editor", "cycle-a", "cycle-b", "viewer"
        );
        assertThat(RoleGovernancePolicy.expandClosureBfs(null, role -> List.of())).isEmpty();
        assertThat(RoleGovernancePolicy.expandClosureBfs(List.of(), role -> List.of())).isEmpty();
        assertThat(RoleGovernancePolicy.expandClosureBfs(List.of("leaf"), role -> null))
                .containsExactly("leaf");

        assertThat(RoleCompositeExpander.IDENTITY.expandCompositeClosure("acme", null)).isEmpty();
        assertThat(RoleCompositeExpander.IDENTITY.expandCompositeClosure("acme", List.of())).isEmpty();
        assertThat(RoleCompositeExpander.IDENTITY.expandCompositeClosure(
                "acme", Arrays.asList(" editor ", null, " ")))
                .containsExactly("editor");
    }

    @Test
    void shouldSupportObjectProviderConstructorAndComputeHighestTier() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RoleCompositeExpander> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        RoleGovernancePolicy fallbackPolicy = new RoleGovernancePolicy(emptyProvider);

        assertThat(fallbackPolicy.highestTier(null)).isEqualTo(RoleTier.TIER_3_APP_USER);
        assertThat(fallbackPolicy.highestTier(List.of())).isEqualTo(RoleTier.TIER_3_APP_USER);
        assertThat(fallbackPolicy.highestTier(Arrays.asList(" ", null))).isEqualTo(RoleTier.TIER_3_APP_USER);
        assertThat(fallbackPolicy.highestTier(List.of("editor", "viewer"))).isEqualTo(RoleTier.TIER_3_APP_USER);
        assertThat(fallbackPolicy.highestTier(List.of("editor", "awgment-account-read")))
                .isEqualTo(RoleTier.TIER_2_OPERATOR_ADMIN);
        assertThat(fallbackPolicy.highestTier(List.of("awgment-account-read", "awgment-account-admin")))
                .isEqualTo(RoleTier.TIER_1_APPROVER);
        assertThat(fallbackPolicy.highestTier(List.of("awgment-account-admin", "awgment-platform-admin")))
                .isEqualTo(RoleTier.TIER_0_PLATFORM);

        @SuppressWarnings("unchecked")
        ObjectProvider<RoleCompositeExpander> populatedProvider = mock(ObjectProvider.class);
        RoleCompositeExpander customExpander = (realm, roles) -> Set.of("awgment-account-admin");
        when(populatedProvider.getIfAvailable()).thenReturn(customExpander);
        RoleGovernancePolicy wiredPolicy = new RoleGovernancePolicy(populatedProvider);
        assertThat(wiredPolicy.expandEffectiveRoles("acme", List.of("custom-wrapper")))
                .containsExactlyInAnyOrder("custom-wrapper", "awgment-account-admin");
    }

    @Test
    void assertCanManageTargetUserShouldEnforceHigherTierAndNonAdminCallerGuards() {
        Map<String, List<String>> compositeGraph = Map.of(
                "nested-approver-wrapper", List.of("intermediate-wrapper"),
                "intermediate-wrapper", List.of("awgment-account-admin"),
                "cycle-wrapper", List.of("cycle-wrapper-2"),
                "cycle-wrapper-2", List.of("cycle-wrapper", "awgment-platform-admin")
        );
        RoleCompositeExpander expander = (realm, directRoles) ->
                RoleGovernancePolicy.expandClosureBfs(directRoles, r -> compositeGraph.getOrDefault(r, List.of()));
        RoleGovernancePolicy policy = new RoleGovernancePolicy(expander);

        // Tier 2 managing Tier 3 and same Tier 2 is allowed
        assertThatCode(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-account-all"), List.of("editor", "awgment-user")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-account-all"), List.of("awgment-account-read")))
                .doesNotThrowAnyException();

        // Tier 2 managing direct Tier 1 or composite-hidden Tier 1 fails with 403 ROLE_ESCALATION_FORBIDDEN
        assertEscalationForbidden(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-account-all"), List.of("awgment-account-admin")));
        assertEscalationForbidden(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-account-all"), List.of("nested-approver-wrapper")));

        // Tier 1 managing Tier 1 / Tier 2 / Tier 3 is allowed, but managing Tier 0 fails
        assertThatCode(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-account-admin"), List.of("awgment-account-admin")))
                .doesNotThrowAnyException();
        assertEscalationForbidden(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-account-admin"), List.of("cycle-wrapper")));

        // Tier 3 (or empty) caller is always rejected even for empty-role target
        assertEscalationForbidden(() -> policy.assertCanManageTargetUser(
                "acme", List.of("awgment-user", "editor"), List.of("viewer")));
        assertEscalationForbidden(() -> policy.assertCanManageTargetUser(
                "acme", List.of(), List.of()));
    }

    @Test
    void assertCanAssignRolesShouldEnforceTier0And1BanTier2StrictSubsetAndTier3Freedom() {
        Map<String, List<String>> compositeGraph = Map.of(
                "disguised-tier1", List.of("inner-tier1"),
                "inner-tier1", List.of("awgment-account-admin"),
                "disguised-tier0", List.of("awgment-platform-admin"),
                "disguised-tier2-read", List.of("awgment-account-read"),
                "disguised-tier2-sys", List.of("awgment-system-service"),
                "pure-tier3-bundle", List.of("editor", "viewer")
        );
        RoleCompositeExpander expander = (realm, directRoles) ->
                RoleGovernancePolicy.expandClosureBfs(directRoles, r -> compositeGraph.getOrDefault(r, List.of()));
        RoleGovernancePolicy policy = new RoleGovernancePolicy(expander);

        // Tier 1 Approver and Tier 2 Operator can freely assign/remove Tier 3 roles without holding them
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-admin"), null, List.of("pure-tier3-bundle", "awgment-user")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-create-or-update"), List.of("editor"), List.of("viewer")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-create-or-update"), null, null))
                .doesNotThrowAnyException();

        // Tier 0 and Tier 1 are NEVER assignable or removable via user role APIs (direct or composite)
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-admin"), List.of(), List.of("awgment-account-admin")));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-admin"), List.of("awgment-account-admin"), List.of()));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-admin"), List.of(), List.of("disguised-tier1")));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-platform-admin"), List.of(), List.of("disguised-tier0")));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-all"), List.of("disguised-tier1"), List.of()));

        // Strict Tier 2 subset check for BOTH added and removed roles
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme",
                List.of("awgment-account-all", "awgment-account-read"),
                List.of("viewer"),
                List.of("awgment-account-read", "editor")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme",
                List.of("awgment-account-all", "awgment-account-read"),
                List.of("awgment-account-read"),
                List.of("editor")))
                .doesNotThrowAnyException();

        // Operator missing Tier 2 role cannot add or remove it (even via composite or unknown awgment-* role)
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-all"), List.of(), List.of("awgment-acl-read")));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-all"), List.of("awgment-acl-read"), List.of()));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-all"), List.of(), List.of("disguised-tier2-sys")));
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-all"), List.of(), List.of("awgment-custom-privileged")));

        // Tier 1 Approver also cannot grant a Tier 2 role they do not hold, but can grant one they do hold
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-admin"), List.of(), List.of("awgment-account-read")));
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme", List.of("awgment-account-admin", "disguised-tier2-read"), List.of(), List.of("awgment-account-read")))
                .doesNotThrowAnyException();

        // Keeping an existing Tier 2 role untouched while modifying Tier 3 roles on same-tier target is allowed
        assertThatCode(() -> policy.assertCanAssignRoles(
                "acme",
                List.of("awgment-account-create-or-update"),
                List.of("awgment-account-read"),
                List.of("awgment-account-read", "editor")))
                .doesNotThrowAnyException();

        // Higher-tier target protection: Tier 2 cannot mutate even Tier 3 roles on a Tier 1 target
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme",
                List.of("awgment-account-all"),
                List.of("awgment-account-admin"),
                List.of("awgment-account-admin", "editor")));

        // Tier 3 caller cannot assign any roles
        assertEscalationForbidden(() -> policy.assertCanAssignRoles(
                "acme", List.of("editor"), List.of(), List.of("viewer")));
    }

    @Test
    void assertNotSelfActionShouldBlockMatchingNonBlankSubAndIgnoreBlankOrDifferentSubs() {
        RoleGovernancePolicy policy = new RoleGovernancePolicy();

        assertThatThrownBy(() -> policy.assertNotSelfAction("sub-123", "sub-123", "disable"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.SELF_ACTION_FORBIDDEN))
                .hasMessageContaining("disable");

        assertThatThrownBy(() -> policy.assertNotSelfAction("sub-123", "sub-123", "  "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.SELF_ACTION_FORBIDDEN));

        assertThatThrownBy(() -> policy.assertNotSelfAction("sub-123", "sub-123", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.SELF_ACTION_FORBIDDEN));

        assertThatCode(() -> policy.assertNotSelfAction("sub-123", "sub-456", "disable"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertNotSelfAction(null, "sub-456", "disable"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertNotSelfAction("sub-123", null, "disable"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertNotSelfAction("  ", "  ", "disable"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertNotSelfAction(null, null, "disable"))
                .doesNotThrowAnyException();
    }

    private static void assertEscalationForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.ROLE_ESCALATION_FORBIDDEN));
    }
}
