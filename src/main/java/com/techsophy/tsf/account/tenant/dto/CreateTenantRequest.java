package com.techsophy.tsf.account.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for provisioning a new tenant schema and verifying its Keycloak 26 realm (UC-TENANT-01).
 */
public record CreateTenantRequest(
        String tenantId,
        @NotBlank(message = "displayName must not be blank")
        @Size(max = 150, message = "displayName must not exceed 150 characters")
        String displayName,
        @Email(message = "bootstrapAdminEmail must be a valid email address")
        @Size(max = 255, message = "bootstrapAdminEmail must not exceed 255 characters")
        String bootstrapAdminEmail,
        @Size(max = 100, message = "bootstrapAdminFirstName must not exceed 100 characters")
        String bootstrapAdminFirstName,
        @Size(max = 100, message = "bootstrapAdminLastName must not exceed 100 characters")
        String bootstrapAdminLastName
) {
}
