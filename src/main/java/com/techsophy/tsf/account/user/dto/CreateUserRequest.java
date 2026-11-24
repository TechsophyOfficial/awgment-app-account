package com.techsophy.tsf.account.user.dto;

import com.techsophy.tsf.account.user.IdentitySource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator request payload for provisioning a tenant user (UC-USER-01).
 * Intentionally excludes privileged lifecycle fields ({@code status}, {@code keycloakSub}, {@code provisioningMode})
 * so unknown-field injection fails with {@code 400 IMMUTABLE_FIELD_MODIFICATION}.
 */
public record CreateUserRequest(
        @NotBlank(message = "userName must not be blank")
        @Size(min = 3, max = 100, message = "userName must be between 3 and 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9._@-]+$", message = "userName contains unsupported characters")
        String userName,
        @NotBlank(message = "emailId must not be blank")
        @Email(message = "emailId must be a valid email address")
        @Size(max = 255, message = "emailId must not exceed 255 characters")
        String emailId,
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
        IdentitySource identitySource,
        @Size(max = 100, message = "idpAlias must not exceed 100 characters")
        String idpAlias,
        List<String> roles,
        List<String> groups
) {

    public CreateUserRequest {
        identitySource = identitySource != null ? identitySource : IdentitySource.KEYCLOAK_LOCAL;
        attributes = attributes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        roles = roles == null ? null : Collections.unmodifiableList(new ArrayList<>(roles));
        groups = groups == null ? null : Collections.unmodifiableList(new ArrayList<>(groups));
    }
}
