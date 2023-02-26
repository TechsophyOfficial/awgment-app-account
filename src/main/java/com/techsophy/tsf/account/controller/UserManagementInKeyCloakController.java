package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(BASE_URL+ VERSION_V1 + KEYCLOAK_URL)
public interface UserManagementInKeyCloakController
{
    @PostMapping(USERS_URL)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<Map<String,Object>> createUser(@RequestBody UserDataSchema userData) throws JsonProcessingException;

    @DeleteMapping(USERS_URL)
    @PreAuthorize(DELETE_OR_ALL_ACCESS)
    ApiResponse<Void> deleteUser(@RequestParam(USER_NAME) String userName) throws JsonProcessingException, UnsupportedEncodingException;

    @PostMapping(USER_ROLES_URL)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<Void> assignRoleToUser( @RequestBody UserRolesSchema userData) throws JsonProcessingException;

    @GetMapping(ROLES_URL)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<List<RolesSchema>> getAllRoles() throws JsonProcessingException;

    @PostMapping(USER_GROUPS_URL)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<Void> assignGroupToUser(@RequestBody UserGroupsSchema userData) throws JsonProcessingException;

    @PostMapping(CHANGE_PASSWORD)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<Void> changePassword() throws JsonProcessingException;

    @PostMapping(SET_PASSWORD)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<Map<String,Object>> setPassword(@RequestParam(USER_NAME) String userName) throws JsonProcessingException, UnsupportedEncodingException;

     @PostMapping("/{clientName}/roles")
     ApiResponse<String> addRoles(@PathVariable(value = CLIENT_NAME,required = true) String clienName, @RequestBody RolesDto rolesDto) throws JsonProcessingException;
}
