package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.dto.GroupsSaveSchema;
import com.techsophy.tsf.account.dto.GroupsSchema;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(BASE_URL + VERSION_V1+ KEYCLOAK_URL)
public interface GroupsController
{
    @GetMapping(GROUPS_URL)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<Stream<GroupsSaveSchema>> getAllGroups(HttpServletRequest request, @RequestParam(value = PAGE, required = false) Integer page,
                                                       @RequestParam(value = SIZE, required = false) Integer pageSize,
                                                       @RequestParam(value = SORT_BY, required = false) String sortBy) throws JsonProcessingException;

    @GetMapping(GROUP_BY_ID_URL)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<GroupsSchema> getGroupById(HttpServletRequest request, @PathVariable(ID) String id) throws JsonProcessingException;

    @PostMapping(GROUPS_URL)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<List<GroupsSaveSchema>> createGroup(HttpServletRequest request, @RequestBody GroupsSchema groupsSchema) throws JsonProcessingException;

    @DeleteMapping(GROUP_BY_ID_URL)
    @PreAuthorize(DELETE_OR_ALL_ACCESS)
    ApiResponse<Void> deleteGroup(HttpServletRequest request, @PathVariable(ID) String id) throws JsonProcessingException;
}
