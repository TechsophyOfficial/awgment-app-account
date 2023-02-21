package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserManagementInKeyCloakControllerImpl;
import com.techsophy.tsf.account.dto.RolesSchema;
import com.techsophy.tsf.account.dto.UserDataSchema;
import com.techsophy.tsf.account.dto.UserGroupsSchema;
import com.techsophy.tsf.account.dto.UserRolesSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserManagementInKeyCloak;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.ThemesConstants.USER_NAME;
import static com.techsophy.tsf.account.constants.UserConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementKeycloakControllerTest
{
    @InjectMocks
    UserManagementInKeyCloakControllerImpl userManagementInKeyCloakController;
    @Mock
    UserManagementInKeyCloak userManagementInKeyCloak;
    @MockBean
    WebClientWrapper webClientWrapper;
    @Value("${user.management.keycloak-api}")
    private String keyCloakApi;
    @Value("${user.management.user-api}")
    private String userCreationApi;
    @Value("${user.management.roles-api}")
    private String getRolesApi;
    @Mock
    GlobalMessageSource globalMessageSource;

    MockHttpServletRequest request = new MockHttpServletRequest();
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    String token;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @BeforeEach
    public void init() {
        token = "token";
        roles.add("5aa92c95-310e-4411-a031-d0d0f6c5b30d");
        groups.add("e8d5cea9-1215-45e9-b54a-0f7a2d6e0880");
    }

    @Test
    @Order(1)
    void testSaveUser() throws Exception {
        Map<String, Object> userData = new HashMap<>();
        userData.put("userName", "Bhavya");
        userData.put("firstName", "bhavya");
        userData.put("lastName", "Sai");
        userData.put("mobileNumber", "1234567890");
        userData.put("emailId", "bhavya.a@techsophy.com");
        userData.put("department", "dev");
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", "6f1ed74b-e064-4151-97e4-4f8ce4e30d75");
        UserDataSchema userDataSchema = new UserDataSchema(userData, null);
        when(userManagementInKeyCloak.createUser(userDataSchema)).thenReturn(userResponse);
        ApiResponse responseEntity = userManagementInKeyCloakController.createUser(userDataSchema);
        Map<String, Object> userId = userManagementInKeyCloak.createUser(userDataSchema);
        assertEquals(1, userId.size());
        assertEquals(true, responseEntity.getSuccess());
        verify(userManagementInKeyCloak, times(1)).createUser(userDataSchema);
    }

    @Test
    @Order(2)
    void testdeleteUser() throws Exception {
        ApiResponse responseEntity = userManagementInKeyCloakController.deleteUser(NAME);
        assertEquals(true, responseEntity.getSuccess());
        userManagementInKeyCloak.deleteUser(NAME);
        verify(userManagementInKeyCloak, times(1)).deleteUser(NAME);
    }

    @Test
    @Order(3)
    void testAssignRoles() throws Exception {
        UserRolesSchema userRolesSchema = new UserRolesSchema(ID, roles);
        ApiResponse responseEntity = userManagementInKeyCloakController.assignRoleToUser(userRolesSchema);
        userManagementInKeyCloak.assignUserRole(userRolesSchema);
        assertEquals(true, responseEntity.getSuccess());
        verify(userManagementInKeyCloak, times(1)).assignUserRole(userRolesSchema);

    }

    @Test
    @Order(4)
    void testGetRoles() throws Exception {
        ApiResponse responseEntity = userManagementInKeyCloakController.getAllRoles();
        when(userManagementInKeyCloak.getAllRoles()).thenReturn(List.of(new RolesSchema(ROLE_ID, ROLE_NAME), new RolesSchema(ROLE_ID, ROLE_NAME)));
        List<RolesSchema> rolesSchemaStream = userManagementInKeyCloak.getAllRoles();
        assertEquals(true, responseEntity.getSuccess());
        assertEquals(2, rolesSchemaStream.size());
        verify(userManagementInKeyCloak, times(1)).getAllRoles();
    }

    @Test
    @Order(5)
    void testAssignGroupToUser() throws JsonProcessingException {
        ApiResponse responseEntity = userManagementInKeyCloakController.assignGroupToUser(new UserGroupsSchema(userID, groups));
        userManagementInKeyCloak.assignUserGroup(new UserGroupsSchema(userID, groups));
        assertEquals(true, responseEntity.getSuccess());
        verify(userManagementInKeyCloak, times(1)).assignUserGroup(new UserGroupsSchema(userID, groups));
    }

    @Test
    @Order(6)
    void testChangePassword() throws JsonProcessingException {
        ApiResponse responseEntity = userManagementInKeyCloakController.changePassword();
        userManagementInKeyCloak.changePassword();
        assertEquals(true, responseEntity.getSuccess());
        verify(userManagementInKeyCloak, times(1)).changePassword();
    }

    @Test
    @Order(7)
    void testSetPassword() throws JsonProcessingException, UnsupportedEncodingException {
        ApiResponse responseEntity = userManagementInKeyCloakController.setPassword(USER_NAME);
        userManagementInKeyCloak.setPassword(USER_NAME);
        assertEquals(true, responseEntity.getSuccess());
        verify(userManagementInKeyCloak, times(1)).setPassword(USER_NAME);
    }
}
