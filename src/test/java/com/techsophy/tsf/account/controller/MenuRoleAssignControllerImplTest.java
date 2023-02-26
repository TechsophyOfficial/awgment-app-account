package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.MenuRoleAssignControllerImpl;
import com.techsophy.tsf.account.dto.MenuRoleAssignResponseSchema;
import com.techsophy.tsf.account.dto.MenuRoleAssignSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.MenuRoleAssignService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.techsophy.tsf.account.constants.AccountConstants.ROLES;
import static com.techsophy.tsf.account.constants.UserConstants.ID;


@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
 class MenuRoleAssignControllerImplTest {
    @InjectMocks
    MenuRoleAssignControllerImpl menuRoleAssignController;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    MenuRoleAssignService menuRoleAssignService;

    @Test
    void createMenuRole() throws IOException {
        List<String> menu = new ArrayList<>();
        menu.add("abc");
        menu.add("abc");
        menu.add("abc");
        MenuRoleAssignSchema menuRoleAssignSchema = new MenuRoleAssignSchema(ID, ROLES, menu);
        ApiResponse<MenuRoleAssignResponseSchema> response = menuRoleAssignController.createMenuRoles(menuRoleAssignSchema);
        Assertions.assertNotNull(response);
    }

    @Test
    void getMenuRoleByIdTest() {
        ApiResponse<MenuRoleAssignSchema> response = menuRoleAssignController.getMenuRoleById(ID);
        Assertions.assertNotNull(response);
    }

    @Test()
    void deleteMenuRolesByIdTest() {
        ApiResponse response = menuRoleAssignController.deleteMenuRolesById(ID);
        Assertions.assertNotNull(response);
    }

    @Test()
    void getAssignedMenuToUserRolesTest() {
        ApiResponse<List<MenuSchema>> response = menuRoleAssignController.getAssignedMenuToUserRoles();
        Assertions.assertNotNull(response);
    }

    @Test()
    void getAllMenuRolesTest() {
        ApiResponse<Stream<MenuRoleAssignSchema>> response = menuRoleAssignController.getAllMenuRoles();
        Assertions.assertNotNull(response);
    }
}

