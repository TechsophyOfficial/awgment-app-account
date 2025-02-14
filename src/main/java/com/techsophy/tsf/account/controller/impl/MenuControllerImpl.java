package com.techsophy.tsf.account.controller.impl;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.MenuController;
import com.techsophy.tsf.account.dto.MenuResponseSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.MenuService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@RequiredArgsConstructor
public class MenuControllerImpl implements MenuController
{
    private final GlobalMessageSource globalMessageSource;
    private final MenuService menuService;

    @Override
    public ApiResponse<MenuResponseSchema> createMenu(MenuSchema menuSchema) throws IOException
    {
        MenuResponseSchema menuResponseSchema = menuService.saveMenu(menuSchema);
        return new ApiResponse<>(menuResponseSchema,true, globalMessageSource.get(SAVE_MENU_SUCCESS));
    }

    @Override
    public ApiResponse<MenuSchema> getMenuById(String id)
    {
        return new ApiResponse<>(menuService.getMenuById(id),true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    @Override
    public ApiResponse<Stream<MenuSchema>> getAllMenus()
    {
        return new ApiResponse<>(menuService.getAllMenus(),true, globalMessageSource.get(GET_MENU_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteMenuById(String id)
    {
        menuService.deleteMenuById(id);
        return new ApiResponse<>(null,true, globalMessageSource.get(DELETE_MENU_SUCCESS));
    }
}
