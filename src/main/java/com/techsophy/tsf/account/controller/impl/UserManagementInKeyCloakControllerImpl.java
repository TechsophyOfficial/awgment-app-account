package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.UserManagementInKeyCloakController;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserManagementInKeyCloak;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserManagementInKeyCloakControllerImpl implements UserManagementInKeyCloakController
{
    private final UserManagementInKeyCloak userManagementInKeyCloak;
    private final GlobalMessageSource globalMessageSource;

    @Override
    public ApiResponse<Map<String, Object>> createUser(@RequestBody UserDataSchema userData) throws JsonProcessingException
    {
        Map<String, Object> userId = userManagementInKeyCloak.createUser(userData);
        return new ApiResponse<>(userId, true, globalMessageSource.get(SAVE_USER_SUCCESS));
    }

    @Override
    public ApiResponse<Void> assignRoleToUser(@RequestBody @Validated UserRolesSchema userRoles) throws JsonProcessingException
    {
        userManagementInKeyCloak.assignUserRole(userRoles);
        return new ApiResponse<>(null, true, globalMessageSource.get(ROLE_USER_SUCCESS));
    }

    @Override
    public ApiResponse<List<RolesSchema>> getAllRoles() throws JsonProcessingException
    {
        List<RolesSchema> rolesSchemaStream = userManagementInKeyCloak.getAllRoles();
        return new ApiResponse<>(rolesSchemaStream, true, globalMessageSource.get(GET_ROLE_SUCCESS));
    }

    @Override
    public ApiResponse<Void> assignGroupToUser(@RequestBody @Validated UserGroupsSchema userData) throws JsonProcessingException
    {
        userManagementInKeyCloak.assignUserGroup( userData);
        return new ApiResponse<>(null, true, globalMessageSource.get(GROUPS_USER_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteUser(@RequestParam(USER_NAME) String userName) throws JsonProcessingException, UnsupportedEncodingException {
        userManagementInKeyCloak.deleteUser(userName);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_USER_SUCCESS));
    }

    @Override
    public ApiResponse<Void> changePassword() throws JsonProcessingException
    {
        userManagementInKeyCloak.changePassword();
        return new ApiResponse<>(null, true, globalMessageSource.get(PASSWORD_UPDATED_SUCCESSFULLY));
    }

    @Override
    public ApiResponse<Map<String,Object>> setPassword(String userName) throws JsonProcessingException, UnsupportedEncodingException {
        Map<String, Object> userDetails=  userManagementInKeyCloak.setPassword(userName);
        return new ApiResponse<>(userDetails, true, globalMessageSource.get(PASSWORD_SET_SUCCESSFULLY));
    }

    @Override
    public ApiResponse<String> addRoles(String clientName, RolesDto rolesDto) {
            return new ApiResponse<>(userManagementInKeyCloak.addRoles(clientName, rolesDto), true, "role added successfully");
    }
}
