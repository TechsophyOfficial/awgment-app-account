package com.techsophy.tsf.account.governance;

/**
 * 4-tier administrative privilege hierarchy enforced by {@link RoleGovernancePolicy}.
 * Smaller numeric levels represent strictly higher authority ({@code 0 < 1 < 2 < 3}).
 */
public enum RoleTier {

    TIER_0_PLATFORM(0),
    TIER_1_APPROVER(1),
    TIER_2_OPERATOR_ADMIN(2),
    TIER_3_APP_USER(3);

    private final int level;

    RoleTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isHigherPrivilegeThan(RoleTier other) {
        return this.level < other.level;
    }
}
