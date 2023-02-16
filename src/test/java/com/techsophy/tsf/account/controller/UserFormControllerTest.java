package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserFormDataControllerImpl;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.HashMap;
import java.util.Map;

import static com.techsophy.tsf.account.constants.UserFormDataConstants.userID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@EnableWebMvc
@ExtendWith(MockitoExtension.class)
class UserFormControllerTest
{
    @InjectMocks
    UserFormDataControllerImpl userFormDataController;
    @Mock
    private HttpHeaders headers;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    UserFormDataService userFormDataService;
    @Mock
    UserServiceImpl userService;
    @Mock
    TokenUtils mockTokenUtils;

    String token;
    Map<String, Object> userData = new HashMap<>();

    @BeforeEach
    public void init()
    {
        token = "token";
        userData.put("userName", "Bhavya");
        userData.put("firstName", "bhavya");
        userData.put("lastName", "Sai");
        userData.put("mobileNumber", "1234567890");
        userData.put("emailId", "bhavya.a@techsophy.com");
        userData.put("department", "dev");
    }

/*
    @Test
    public void saveUserTest() throws IOException
    {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-USERID", "X-Test-Value");
        ApiResponse responseEntity = userFormDataController.saveUser(new UserFormDataSchema(null, userData, userID, FORM_VERSION), httpHeaders);
        when(userFormDataService.saveForm(new UserFormDataSchema(null, userData, userID, FORM_VERSION), "X-Test-Value")).thenReturn(new UserFormDataSchema(ID, userData, userID, FORM_VERSION));
        UserFormDataSchema userFormDataSchema = userFormDataService.saveForm(new UserFormDataSchema(null, userData, userID, FORM_VERSION), "X-Test-Value");
        assertThat(userFormDataSchema.getUserData()).isNotNull();
        assertEquals(true, responseEntity.getSuccess());
        verify(userFormDataService, times(1)).saveForm(new UserFormDataSchema(null, userData, userID, FORM_VERSION), "X-Test-Value");
    }
*/

    @Test
    void getFormByUserId()
    {
        when(userFormDataService.getUserFormDataByUserId(userID, true)).thenReturn(new UserFormDataSchema( userData, null, null));
        ApiResponse responseEntity = userFormDataController.getUserByUserId(userID, true);
        assertEquals(true, responseEntity.getSuccess());
        verify(userFormDataService, times(1)).getUserFormDataByUserId(userID, true);
    }

/*
    @Test
    public void getAllForms1()
    {
        String[] list = new String[0];
        Sort sort = mockUtilityService.getSortBy(list);
        PaginationResponsePayload paginationResponsePayload = new PaginationResponsePayload();
        paginationResponsePayload.setContent(List.of(new UserFormDataSchema(null, userData, null, null)));
        ApiResponse response = userFormDataController.getAllUsers("q", true, 1, 1, list, "", "");
        when(userFormDataService.getAllUserFormDataObjects(any(), (Pageable) any())).thenReturn(paginationResponsePayload);
        PaginationResponsePayload paginationResponsePayload1 = userFormDataService.getAllUserFormDataObjects(any(), (Pageable) any());
        assertThat(response.getSuccess()).isEqualTo(true);
        assertThat(paginationResponsePayload1.getContent()).isNotNull();
    }*/
/*
    @Test
    public void getAllForms()
    {
        String[] list = new String[0];
        Sort sort = mockUtilityService.getSortBy(list);
        when(userFormDataService.getAllUserFormDataObjects(true,sort)).thenReturn(Collections.singletonList(new UserFormDataSchema(null,userData,null,null)));
        ApiResponse response = userFormDataController.getAllUsers(, true, null, 1, list, "", "");
        List<UserData> userData= userFormDataService.getAllUserFormDataObjects(true,sort);
        assertThat(response.getSuccess()).isEqualTo(true);
        assertEquals(1,userData.size());
    }*/

 /*   @Test
    public void getAllFormsOnlyMandatory()
    {
        String[] list = new String[0];
        Sort sort = mockUtilityService.getSortBy(list);
        PaginationResponsePayload paginationResponsePayload = new PaginationResponsePayload();
        paginationResponsePayload.setContent(List.of(new UserFormDataSchema(null, userData, null, null)));
        when(userService.getAllUsersByFilter("q", any(), any())).thenReturn(List.of(new UserFormDataSchema(null, userData, null, null)));
        ApiResponse response = userFormDataController.getAllUsers("q", true, 1, 1, list, "col", "value");
        List<UserData> userData=userService.getAllUsersByFilter("q", any(), any());
        assertThat(response.getSuccess()).isEqualTo(true);
        assertThat(userData).isNotNull();
    }*/
}
