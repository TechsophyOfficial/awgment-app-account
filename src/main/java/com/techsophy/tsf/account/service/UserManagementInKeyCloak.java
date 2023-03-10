package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.*;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface UserManagementInKeyCloak
{
    Map<String,Object> createUser(UserDataSchema userData) throws JsonProcessingException;

    List<RolesSchema> getAllRoles() throws JsonProcessingException;

    void assignUserRole(UserRolesSchema userData) throws JsonProcessingException;

    void deleteUser(String userName) throws JsonProcessingException, UnsupportedEncodingException;

    Stream<GroupsSaveSchema> getAllGroups() throws JsonProcessingException;

    GroupsSchema getGroupById(String id) throws JsonProcessingException;

    List<GroupsSaveSchema> createGroup(GroupsSchema groupsSchema) throws JsonProcessingException;

    void deleteGroup(String id);

    void assignUserGroup(UserGroupsSchema userGroupsSchema) throws JsonProcessingException;

    void changePassword() throws JsonProcessingException;
    Map<String,Object> setPassword(String userName) throws JsonProcessingException, UnsupportedEncodingException;

    String addRoles(String clientName, RolesDto rolesDto);

}
