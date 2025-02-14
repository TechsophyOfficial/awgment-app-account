package com.techsophy.tsf.account.controller.impl;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.MenuRoleAssignController;
import com.techsophy.tsf.account.dto.MenuRoleAssignResponseSchema;
import com.techsophy.tsf.account.dto.MenuRoleAssignSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.MenuRoleAssignService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of {@link MenuRoleAssignController} responsible for handling
 * menu role assignments.
 */
@RestController
@RequiredArgsConstructor
public class MenuRoleAssignControllerImpl implements MenuRoleAssignController {
    private final GlobalMessageSource globalMessageSource;
    private final MenuRoleAssignService menuRoleAssignService;

    /**
     * Creates a new menu role assignment.
     *
     * @param menuRoleAssignSchema The details of the menu role assignment.
     * @return ApiResponse containing the saved menu role assignment.
     * @throws IOException If an error occurs while processing the menu role data.
     */
    @Override
    public ApiResponse<MenuRoleAssignResponseSchema> createMenuRoles(MenuRoleAssignSchema menuRoleAssignSchema) throws IOException {
        MenuRoleAssignResponseSchema menuRoleAssignResponseSchema = menuRoleAssignService.saveMenuRole(menuRoleAssignSchema);
        return new ApiResponse<>(menuRoleAssignResponseSchema, true, globalMessageSource.get(SAVE_MENU_SUCCESS));
    }

    /**
     * Retrieves a menu role assignment by its ID.
     *
     * @param id The ID of the menu role assignment.
     * @return ApiResponse containing the menu role assignment details.
     */
    @Override
    public ApiResponse<MenuRoleAssignSchema> getMenuRoleById(String id) {
        return new ApiResponse<>(menuRoleAssignService.getMenuRole(id), true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    /**
     * Retrieves all menu role assignments.
     *
     * @return ApiResponse containing a stream of all menu role assignments.
     */
    @Override
    public ApiResponse<Stream<MenuRoleAssignSchema>> getAllMenuRoles() {
        return new ApiResponse<>(menuRoleAssignService.getAllMenuRole(), true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    /**
     * Retrieves all assigned menus for user roles.
     *
     * @return ApiResponse containing a list of assigned menus.
     */
    @Override
    public ApiResponse<List<MenuSchema>> getAssignedMenuToUserRoles() {
        return new ApiResponse<>(menuRoleAssignService.getAssignedMenuToUserRoles(), true, globalMessageSource.get(GET_MENU_ROLE_SUCCESS));
    }

    /**
     * Deletes a menu role assignment by its ID.
     *
     * @param id The ID of the menu role assignment to be deleted.
     * @return ApiResponse indicating the success of the delete operation.
     */
    @Override
    public ApiResponse<Void> deleteMenuRolesById(String id) {
        menuRoleAssignService.deleteMenuRoleById(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_MENU_SUCCESS));
    }
}
