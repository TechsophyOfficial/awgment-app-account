package com.techsophy.tsf.account.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-service profile update payload for {@code PUT /accounts/v2/users/me} (UC-USER-07).
 * Strictly omits admin-managed and identity fields ({@code department}, {@code attributes},
 * {@code userName}, {@code emailId}, {@code status}, {@code roles}, {@code groups},
 * {@code identitySource}, {@code idpAlias}) to prevent BOPLA / ACL attribute tampering.
 */
public record UpdateMyProfileRequest(
        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,
        @Size(max = 100, message = "lastName must not exceed 100 characters")
        String lastName,
        @Size(max = 32, message = "mobileNumber must not exceed 32 characters")
        String mobileNumber,
        @Size(max = 35, message = "locale must not exceed 35 characters")
        String locale,
        @Size(max = 64, message = "timezone must not exceed 64 characters")
        String timezone,
        Map<String, Object> selfServicePreferences,
        @NotNull(message = "version must not be null")
        Long version
) {

    public UpdateMyProfileRequest {
        selfServicePreferences = selfServicePreferences == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(selfServicePreferences));
    }
}
