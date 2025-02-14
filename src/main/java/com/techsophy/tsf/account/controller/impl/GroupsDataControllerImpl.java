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
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of {@link GroupsDataController} that manages operations related to group data.
 */
@RestController
@RequiredArgsConstructor
public class GroupsDataControllerImpl implements GroupsDataController
{
    private final GroupsDataService groupsDataService;
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;

    /**
     * Saves a group. If the group ID is not provided, a new group is created; otherwise, the existing group is updated.
     *
     * @param groupsData The data of the group to be saved.
     * @return ApiResponse containing the saved group schema.
     */
    @SneakyThrows
    @Override
    public ApiResponse<GroupsDataSchema> saveGroup(GroupsData groupsData)
    {
        GroupsDataSchema groupsDataSchema = groupsDataService.saveGroup(groupsData);
        if (StringUtils.isEmpty(groupsData.getId()))
        {
            return new ApiResponse<>(groupsDataSchema, true, globalMessageSource.get(GROUP_CREATE_SUCCESS));
        }
        return new ApiResponse<>(groupsDataSchema, true, globalMessageSource.get(GROUP_UPDATE_SUCCESS));
    }

    /**
     * Retrieves all groups with optional filtering, pagination, and sorting.
     *
     * @param q              The query string for filtering groups.
     * @param page           The page number for pagination.
     * @param pageSize       The number of items per page.
     * @param sortBy         The sorting criteria.
     * @param deploymentList The list of deployments to filter by.
     * @return ApiResponse containing paginated or non-paginated group data.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse getAllGroups(String q, Integer page, Integer pageSize, String[] sortBy, String deploymentList) throws JsonProcessingException
    {
        if (page == null)
        {
            return new ApiResponse<>(groupsDataService.getAllGroups(q, tokenUtils.getSortBy(sortBy), deploymentList), true,
                    globalMessageSource.get(GET_GROUP_SUCCESS));
        }
        PaginationResponsePayload groupsDataSchema = groupsDataService.getAllGroups(q, tokenUtils.getPageRequest(page, pageSize, sortBy));
        return new ApiResponse<>(groupsDataSchema, true, globalMessageSource.get(GET_GROUP_SUCCESS));
    }

    /**
     * Retrieves a group by its unique identifier.
     *
     * @param id The unique identifier of the group.
     * @return ApiResponse containing the group details.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse<GroupsDataSchema> getGroupById(String id) throws JsonProcessingException
    {
        return new ApiResponse<>(groupsDataService.getGroupById(id), true, globalMessageSource.get(GET_GROUP_BY_ID_SUCCESS));
    }

    /**
     * Deletes a group by its unique identifier.
     *
     * @param id The unique identifier of the group to be deleted.
     * @return ApiResponse confirming the deletion success.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse<Void> deleteGroup(String id) throws JsonProcessingException
    {
        groupsDataService.deleteGroup(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_GROUP_SUCCESS));
    }

    /**
     * Assigns roles to a group.
     *
     * @param id         The unique identifier of the group.
     * @param groupRoles The roles to be assigned to the group.
     * @return ApiResponse confirming the successful role assignment.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
    @Override
    public ApiResponse<GroupsDataSchema> assignRolesToGroup(String id, AssignGroupRoles groupRoles) throws JsonProcessingException
    {
        groupsDataService.assignRolesToGroup(id, groupRoles);
        return new ApiResponse<>(null, true, globalMessageSource.get(ROLES_ASSIGN_SUCCESS_TO_GROUPS));
    }
}
