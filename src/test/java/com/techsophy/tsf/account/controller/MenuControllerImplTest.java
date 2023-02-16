package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.MenuControllerConstant;
import com.techsophy.tsf.account.controller.impl.MenuControllerImpl;
import com.techsophy.tsf.account.dto.MenuResponseSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.stream.Stream;

import static com.techsophy.tsf.account.constants.MenuControllerConstant.*;
import static com.techsophy.tsf.account.constants.UserConstants.ID;


@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
class MenuControllerImplTest
{
    @InjectMocks
    MenuControllerImpl menuControllerImpl;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    MenuService menuService;

    @Test
    void createMenuTest() throws IOException {
        MenuSchema menuSchema = new MenuSchema(ID, MenuControllerConstant.TYPE,LABLE,URL,DIVIDER,VERSION);
        ApiResponse<MenuResponseSchema> response = menuControllerImpl.createMenu(menuSchema);
        Assertions.assertNotNull(response);
    }

    @Test
    void getMenuByIdTest() {
        ApiResponse<MenuSchema> response = menuControllerImpl.getMenuById(ID);
        Assertions.assertNotNull(response);
    }

    @Test()
    void deleteMenuByIdTest() {
        ApiResponse<Void> response = menuControllerImpl.deleteMenuById(ID);
        Assertions.assertNotNull(response);
    }

    @Test()
    void getAllMenusTest() {
        ApiResponse<Stream<MenuSchema>> response = menuControllerImpl.getAllMenus();
        Assertions.assertNotNull(response);
    }
}
