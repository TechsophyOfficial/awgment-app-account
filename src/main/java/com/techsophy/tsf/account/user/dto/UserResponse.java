package com.techsophy.tsf.account.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.ProvisioningMode;
import com.techsophy.tsf.account.user.SyncStatus;
import com.techsophy.tsf.account.user.UserEntity;
import com.techsophy.tsf.account.user.UserStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API response projection for tenant user profiles.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        Long id,
        String keycloakSub,
        String userName,
        String emailId,
        String firstName,
        String lastName,
        String mobileNumber,
        String department,
        String locale,
        String timezone,
        Map<String, Object> selfServicePreferences,
        Map<String, Object> attributes,
        IdentitySource identitySource,
        String idpAlias,
        ProvisioningMode provisioningMode,
        UserStatus status,
        List<String> desiredRoles,
        List<String> desiredGroups,
        SyncStatus syncStatus,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public UserResponse {
        selfServicePreferences = selfServicePreferences == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(selfServicePreferences));
        attributes = attributes == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        desiredRoles = desiredRoles == null ? null : List.copyOf(desiredRoles);
        desiredGroups = desiredGroups == null ? null : List.copyOf(desiredGroups);
    }

    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getKeycloakSub(),
                entity.getUserName(),
                entity.getEmailId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getMobileNumber(),
                entity.getDepartment(),
                entity.getLocale(),
                entity.getTimezone(),
                entity.getSelfServicePreferences() != null ? entity.getSelfServicePreferences() : Map.of(),
                entity.getAttributes() != null ? entity.getAttributes() : Map.of(),
                entity.getIdentitySource(),
                entity.getIdpAlias(),
                entity.getProvisioningMode(),
                entity.getStatus(),
                entity.getDesiredRoles(),
                entity.getDesiredGroups(),
                entity.getSyncStatus(),
                entity.getOptLockVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static UserResponse mandatoryOnly(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                null,
                entity.getUserName(),
                entity.getEmailId(),
                entity.getFirstName(),
                entity.getLastName(),
                null,
                null,
                entity.getLocale(),
                null,
                null,
                null,
                entity.getIdentitySource(),
                null,
                null,
                entity.getStatus(),
                null,
                null,
                entity.getSyncStatus(),
                entity.getOptLockVersion(),
                null,
                null
        );
    }
}
