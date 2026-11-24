package com.techsophy.tsf.account.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operator request payload for updating mutable user profile fields (UC-USER-01).
 * Strictly omits {@code userName}, {@code emailId}, {@code identitySource}, {@code idpAlias},
 * {@code status}, {@code roles}, and {@code groups} so attempts to mutate them trigger
 * {@code 400 IMMUTABLE_FIELD_MODIFICATION}.
 */
public record UpdateUserRequest(
        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,
        @Size(max = 100, message = "lastName must not exceed 100 characters")
        String lastName,
        @Size(max = 32, message = "mobileNumber must not exceed 32 characters")
        String mobileNumber,
        @Size(max = 150, message = "department must not exceed 150 characters")
        String department,
        @Size(max = 35, message = "locale must not exceed 35 characters")
        String locale,
        @Size(max = 64, message = "timezone must not exceed 64 characters")
        String timezone,
        Map<String, Object> attributes,
        @NotNull(message = "version must not be null")
        Long version
) {

    public UpdateUserRequest {
        attributes = attributes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
