package com.techsophy.tsf.account.tenant.dto;

import com.techsophy.tsf.account.tenant.TenantStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for suspending or reactivating a tenant (UC-TENANT-04).
 */
public record UpdateTenantStatusRequest(
        @NotNull(message = "status is required")
        TenantStatus status,
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason,
        @NotNull(message = "version is required")
        @Min(value = 0, message = "version must be >= 0")
        Long version
) {
}
