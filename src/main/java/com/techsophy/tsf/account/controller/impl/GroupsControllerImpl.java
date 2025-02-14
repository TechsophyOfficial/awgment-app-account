package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.GroupsController;
import com.techsophy.tsf.account.dto.GroupsSaveSchema;
import com.techsophy.tsf.account.dto.GroupsSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserManagementInKeyCloak;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@RequiredArgsConstructor
public class GroupsControllerImpl implements GroupsController
{
    private final UserManagementInKeyCloak userManagementInKeyCloak;
    private final GlobalMessageSource globalMessageSource;

    @Override
    public ApiResponse getAllGroups(HttpServletRequest request, Integer page, Integer pageSize, String sortBy) throws JsonProcessingException
    {
            Stream<GroupsSaveSchema> groupsSchemaStream = userManagementInKeyCloak.getAllGroups();
            return new ApiResponse<>(groupsSchemaStream, true, globalMessageSource.get(GET_GROUP_SUCCESS));
    }

    @Override
    public ApiResponse<GroupsSchema> getGroupById(HttpServletRequest request, String id) throws JsonProcessingException
    {
        GroupsSchema groupsSchemaStream = userManagementInKeyCloak.getGroupById(id);
        return new ApiResponse<>(groupsSchemaStream, true, globalMessageSource.get(GET_GROUP_BY_ID_SUCCESS));
      }

    @Override
    public ApiResponse<List<GroupsSaveSchema>> createGroup(HttpServletRequest request, GroupsSchema groupsSchema) throws JsonProcessingException
    {
        List<GroupsSaveSchema> groupData =userManagementInKeyCloak.createGroup(groupsSchema);
        if(StringUtils.isEmpty(groupsSchema.getId()))
        {
            return new ApiResponse<>(groupData, true, globalMessageSource.get(GROUP_CREATE_SUCCESS));
        }
        return new ApiResponse<>(groupData, true, globalMessageSource.get(GROUP_UPDATE_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteGroup(HttpServletRequest request, String id) throws JsonProcessingException
    {
        userManagementInKeyCloak.deleteGroup(id);
        return new ApiResponse<>(null,true, globalMessageSource.get(DELETE_GROUP_SUCCESS));
    }
}
