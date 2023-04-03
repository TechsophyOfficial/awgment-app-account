package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.dto.MenuRoleAssignResponseSchema;
import com.techsophy.tsf.account.dto.MenuRoleAssignSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.AccountConstants.ID;

@RequestMapping(BASE_URL + VERSION_V1)
public interface MenuRoleAssignController
{
    @PostMapping(MENU_ROLE)
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<MenuRoleAssignResponseSchema> createMenuRoles(@RequestBody MenuRoleAssignSchema menuRoleAssignSchema) throws IOException;

    @GetMapping(MENU_ROLE+MENU_ROLE_ID)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<MenuRoleAssignSchema> getMenuRoleById(@PathVariable(ID) String id);

    @GetMapping(MENU_ROLE)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<Stream<MenuRoleAssignSchema>> getAllMenuRoles();

    @GetMapping(ROLES_URL+MENU)
    ApiResponse<List<MenuSchema>> getAssignedMenuToUserRoles();

    @DeleteMapping(MENU_ROLE+MENU_ROLE_ID)
    @PreAuthorize(DELETE_OR_ALL_ACCESS)
    ApiResponse<Void> deleteMenuRolesById(@PathVariable(ID) String id);
}
