package com.techsophy.tsf.account.controller.impl;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.MenuController;
import com.techsophy.tsf.account.dto.MenuResponseSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.stream.Stream;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of {@link MenuController} that manages menu-related operations.
 */
@RestController
@RequiredArgsConstructor
public class MenuControllerImpl implements MenuController {
    private final GlobalMessageSource globalMessageSource;
    private final MenuService menuService;

    /**
     * Creates a new menu entry.
     *
     * @param menuSchema The menu details to be saved.
     * @return ApiResponse containing the saved menu details.
     * @throws IOException If an error occurs while processing the menu data.
     */
    @Override
    public ApiResponse<MenuResponseSchema> createMenu(MenuSchema menuSchema) throws IOException {
        MenuResponseSchema menuResponseSchema = menuService.saveMenu(menuSchema);
        return new ApiResponse<>(menuResponseSchema, true, globalMessageSource.get(SAVE_MENU_SUCCESS));
    }

    /**
     * Retrieves a menu by its ID.
     *
     * @param id The ID of the menu to retrieve.
     * @return ApiResponse containing the requested menu details.
     */
    @Override
    public ApiResponse<MenuSchema> getMenuById(String id) {
        return new ApiResponse<>(menuService.getMenuById(id), true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    /**
     * Retrieves all available menus.
     *
     * @return ApiResponse containing a stream of all menu records.
     */
    @Override
    public ApiResponse<Stream<MenuSchema>> getAllMenus() {
        return new ApiResponse<>(menuService.getAllMenus(), true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    /**
     * Deletes a menu by its ID.
     *
     * @param id The ID of the menu to be deleted.
     * @return ApiResponse indicating the success of the delete operation.
     */
    @Override
    public ApiResponse<Void> deleteMenuById(String id) {
        menuService.deleteMenuById(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_MENU_SUCCESS));
    }
}
