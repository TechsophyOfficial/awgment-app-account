package com.techsophy.tsf.account.user.dto;

import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserStatus;

/**
 * Response body returned with {@code 202 Accepted} when synchronous Keycloak revocation
 * defers to the coalesced outbox worker (UC-USER-04 degraded mode).
 */
public record UserDeactivationResponse(
        UserStatus status,
        SyncStatus syncStatus
) {
}
