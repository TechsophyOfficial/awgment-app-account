package com.techsophy.tsf.account.controller.impl;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.MenuRoleAssignController;
import com.techsophy.tsf.account.dto.MenuRoleAssignResponseSchema;
import com.techsophy.tsf.account.dto.MenuRoleAssignSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.MenuRoleAssignService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@RequiredArgsConstructor
public class MenuRoleAssignControllerImpl implements MenuRoleAssignController
{
    private final GlobalMessageSource globalMessageSource;
    private final MenuRoleAssignService menuRoleAssignService;

    @Override
    public ApiResponse<MenuRoleAssignResponseSchema> createMenuRoles(MenuRoleAssignSchema menuRoleAssignSchema) throws IOException
    {
        MenuRoleAssignResponseSchema menuRoleAssignResponseSchema = menuRoleAssignService.saveMenuRole(menuRoleAssignSchema);
        return new ApiResponse<>(menuRoleAssignResponseSchema,true, globalMessageSource.get(SAVE_MENU_SUCCESS));
    }

    @Override
    public ApiResponse<MenuRoleAssignSchema> getMenuRoleById(String id)
    {
        return new ApiResponse<>(menuRoleAssignService.getMenuRole(id),true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    @Override
    public ApiResponse<Stream<MenuRoleAssignSchema>> getAllMenuRoles()
    {
        return new ApiResponse<>(menuRoleAssignService.getAllMenuRole(),true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    @Override
    public ApiResponse<List<MenuSchema>> getAssignedMenuToUserRoles()
    {
        return new ApiResponse<>(menuRoleAssignService.getAssignedMenuToUserRoles(),true,globalMessageSource.get(GET_MENU_ROLE_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteMenuRolesById(String id)
    {
        menuRoleAssignService.deleteMenuRoleById(id);
        return new ApiResponse<>(null,true, globalMessageSource.get(DELETE_MENU_SUCCESS));
    }
}
