package com.techsophy.tsf.account.tenant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techsophy.tsf.account.tenant.TenantStatus;

import java.time.Instant;

/**
 * Response representation of a tenant registry entry.
 */
public record TenantResponse(
        String tenantId,
        String schemaName,
        String displayName,
        TenantStatus status,
        Boolean keycloakContractVerified,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String lastError,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
