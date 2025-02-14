package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.GroupsDataController;
import com.techsophy.tsf.account.dto.AssignGroupRoles;
import com.techsophy.tsf.account.dto.GroupsData;
import com.techsophy.tsf.account.dto.GroupsDataSchema;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.GroupsDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@RequiredArgsConstructor
public class GroupsDataControllerImpl implements GroupsDataController
{
    private final GroupsDataService groupsDataService;
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;

    @SneakyThrows
    @Override
    public ApiResponse<GroupsDataSchema> saveGroup(GroupsData groupsData)
    {
        GroupsDataSchema groupsDataSchema = groupsDataService.saveGroup(groupsData);
        if(StringUtils.isEmpty(groupsData.getId()))
        {
            return new ApiResponse<>(groupsDataSchema, true, globalMessageSource.get(GROUP_CREATE_SUCCESS));
        }
        return new ApiResponse<>(groupsDataSchema, true, globalMessageSource.get(GROUP_UPDATE_SUCCESS));
      }

    @Override
    public ApiResponse getAllGroups(String q, Integer page, Integer pageSize, String[] sortBy,String deploymentList) throws JsonProcessingException
    {
        if (page == null)
        {
            return new ApiResponse<>(groupsDataService.getAllGroups(q, tokenUtils.getSortBy(sortBy),deploymentList), true,
                    globalMessageSource.get(GET_GROUP_SUCCESS));
        }
        PaginationResponsePayload groupsDataSchema = groupsDataService.getAllGroups(q, tokenUtils.getPageRequest(page, pageSize, sortBy));
        return new ApiResponse<>(groupsDataSchema, true, globalMessageSource.get(GET_GROUP_SUCCESS));
    }

    @Override
    public ApiResponse<GroupsDataSchema> getGroupById( String id) throws JsonProcessingException
    {
        return new ApiResponse<>(groupsDataService.getGroupById(id), true, globalMessageSource.get(GET_GROUP_BY_ID_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteGroup(String id) throws JsonProcessingException
    {
        groupsDataService.deleteGroup(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_GROUP_SUCCESS));
    }

    @Override
    public ApiResponse<GroupsDataSchema> assignRolesToGroup(String id, AssignGroupRoles groupRoles) throws JsonProcessingException
    {
        groupsDataService.assignRolesToGroup(id,groupRoles);
        return new ApiResponse<>(null, true, globalMessageSource.get(ROLES_ASSIGN_SUCCESS_TO_GROUPS));
    }
}
