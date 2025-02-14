package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.GroupsController;
import com.techsophy.tsf.account.dto.GroupsSaveSchema;
import com.techsophy.tsf.account.dto.GroupsSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserManagementInKeyCloak;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of {@link GroupsController} that manages group operations.
 */
@RestController
@RequiredArgsConstructor
public class GroupsControllerImpl implements GroupsController
{
    private final UserManagementInKeyCloak userManagementInKeyCloak;
    private final GlobalMessageSource globalMessageSource;

    /**
     * Retrieves all groups.
     *
     * @param request The HTTP request object.
     * @param page    The page number for pagination (not used in the current implementation).
     * @param pageSize The size of each page (not used in the current implementation).
     * @param sortBy  The field to sort by (not used in the current implementation).
     * @return ApiResponse containing a stream of group schemas.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse getAllGroups(HttpServletRequest request, Integer page, Integer pageSize, String sortBy) throws JsonProcessingException
    {
        Stream<GroupsSaveSchema> groupsSchemaStream = userManagementInKeyCloak.getAllGroups();
        return new ApiResponse<>(groupsSchemaStream, true, globalMessageSource.get(GET_GROUP_SUCCESS));
    }

    /**
     * Retrieves a group by its unique identifier.
     *
     * @param request The HTTP request object.
     * @param id      The unique identifier of the group.
     * @return ApiResponse containing the group details.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse<GroupsSchema> getGroupById(HttpServletRequest request, String id) throws JsonProcessingException
    {
        GroupsSchema groupsSchemaStream = userManagementInKeyCloak.getGroupById(id);
        return new ApiResponse<>(groupsSchemaStream, true, globalMessageSource.get(GET_GROUP_BY_ID_SUCCESS));
    }

    /**
     * Creates or updates a group.
     *
     * @param request      The HTTP request object.
     * @param groupsSchema The group schema containing group details.
     * @return ApiResponse containing the created or updated group details.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse<List<GroupsSaveSchema>> createGroup(HttpServletRequest request, GroupsSchema groupsSchema) throws JsonProcessingException
    {
        List<GroupsSaveSchema> groupData = userManagementInKeyCloak.createGroup(groupsSchema);
        if (StringUtils.isEmpty(groupsSchema.getId()))
        {
            return new ApiResponse<>(groupData, true, globalMessageSource.get(GROUP_CREATE_SUCCESS));
        }
        return new ApiResponse<>(groupData, true, globalMessageSource.get(GROUP_UPDATE_SUCCESS));
    }

    /**
     * Deletes a group by its unique identifier.
     *
     * @param request The HTTP request object.
     * @param id      The unique identifier of the group to be deleted.
     * @return ApiResponse confirming the deletion success.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse<Void> deleteGroup(HttpServletRequest request, String id) throws JsonProcessingException
    {
        userManagementInKeyCloak.deleteGroup(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_GROUP_SUCCESS));
    }
}
