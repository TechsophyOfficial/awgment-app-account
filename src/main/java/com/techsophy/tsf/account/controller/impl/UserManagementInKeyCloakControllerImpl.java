package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.UserManagementInKeyCloakController;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserManagementInKeyCloak;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Controller implementation for managing users in Keycloak.
 * This class provides endpoints to create users, assign roles and groups, delete users,
 * and manage passwords within a Keycloak-based authentication system.
 */
@RestController
@RequiredArgsConstructor
public class UserManagementInKeyCloakControllerImpl implements UserManagementInKeyCloakController {
    private final UserManagementInKeyCloak userManagementInKeyCloak;
    private final GlobalMessageSource globalMessageSource;

    /**
     * Creates a new user in Keycloak.
     *
     * @param userData The user data schema containing user details.
     * @return ApiResponse containing the created user's details.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<Map<String, Object>> createUser(@RequestBody UserDataSchema userData) throws JsonProcessingException {
        Map<String, Object> userId = userManagementInKeyCloak.createUser(userData);
        return new ApiResponse<>(userId, true, globalMessageSource.get(SAVE_USER_SUCCESS));
    }

    /**
     * Assigns a role to a user in Keycloak.
     *
     * @param userRoles The user roles schema containing role assignment details.
     * @return ApiResponse indicating success or failure.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<Void> assignRoleToUser(@RequestBody @Validated UserRolesSchema userRoles) throws JsonProcessingException {
        userManagementInKeyCloak.assignUserRole(userRoles);
        return new ApiResponse<>(null, true, globalMessageSource.get(ROLE_USER_SUCCESS));
    }

    /**
     * Retrieves all available roles in Keycloak.
     *
     * @return ApiResponse containing a list of roles.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<List<RolesSchema>> getAllRoles() throws JsonProcessingException {
        List<RolesSchema> rolesSchemaStream = userManagementInKeyCloak.getAllRoles();
        return new ApiResponse<>(rolesSchemaStream, true, globalMessageSource.get(GET_ROLE_SUCCESS));
    }

    /**
     * Assigns a group to a user in Keycloak.
     *
     * @param userData The user groups schema containing group assignment details.
     * @return ApiResponse indicating success or failure.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<Void> assignGroupToUser(@RequestBody @Validated UserGroupsSchema userData) throws JsonProcessingException {
        userManagementInKeyCloak.assignUserGroup(userData);
        return new ApiResponse<>(null, true, globalMessageSource.get(GROUPS_USER_SUCCESS));
    }

    /**
     * Deletes a user from Keycloak based on the username.
     *
     * @param userName The username of the user to be deleted.
     * @return ApiResponse indicating success or failure.
     * @throws JsonProcessingException if JSON processing fails.
     * @throws UnsupportedEncodingException if encoding issues occur.
     */
    @Override
    public ApiResponse<Void> deleteUser(@RequestParam(USER_NAME) String userName) throws JsonProcessingException, UnsupportedEncodingException {
        userManagementInKeyCloak.deleteUser(userName);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_USER_SUCCESS));
    }

    /**
     * Changes the password of the currently authenticated user in Keycloak.
     *
     * @return ApiResponse indicating success or failure.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<Void> changePassword() throws JsonProcessingException {
        userManagementInKeyCloak.changePassword();
        return new ApiResponse<>(null, true, globalMessageSource.get(PASSWORD_UPDATED_SUCCESSFULLY));
    }

    /**
     * Sets a new password for a user in Keycloak.
     *
     * @param userName The username of the user for whom the password is being set.
     * @return ApiResponse containing updated user details.
     * @throws JsonProcessingException if JSON processing fails.
     * @throws UnsupportedEncodingException if encoding issues occur.
     */
    @Override
    public ApiResponse<Map<String, Object>> setPassword(String userName) throws JsonProcessingException, UnsupportedEncodingException {
        Map<String, Object> userDetails = userManagementInKeyCloak.setPassword(userName);
        return new ApiResponse<>(userDetails, true, globalMessageSource.get(PASSWORD_SET_SUCCESSFULLY));
    }

    /**
     * Adds a new role for a client in Keycloak.
     *
     * @param clientName The name of the client.
     * @param rolesDto The role details to be added.
     * @return ApiResponse containing the result of the role addition.
     */
    @Override
    public ApiResponse<String> addRoles(String clientName, RolesDto rolesDto) {
        return new ApiResponse<>(userManagementInKeyCloak.addRoles(clientName, rolesDto), true, "role added successfully");
    }
}
