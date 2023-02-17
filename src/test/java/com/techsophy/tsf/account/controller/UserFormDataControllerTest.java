package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserFormDataControllerImpl;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

//@SpringBootTest
@ActiveProfiles(TEST_ACTIVE_PROFILE)
@ExtendWith(SpringExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserFormDataControllerTest
{
    @Mock
    UserFormDataService userFormDataService;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    TokenUtils tokenUtils;
    @Mock
    HttpHeaders httpHeaders;
    @Mock
    UserDetails userDetails;
    @InjectMocks
    UserFormDataControllerImpl userFormDataController;

    @Test
    void getUserDetailsOfLoggedInUserSuccess() throws JsonProcessingException {
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map = new HashMap<>();
        map.put("id","123");
        list.add(map);
        Mockito.when(userDetails.getUserDetails()).thenReturn(list);
        userFormDataController.getUserDetailsOfLoggedInUser();
        Mockito.verify(userFormDataService,Mockito.times(1)).getUserFormDataByUserId("123",false);
    }
    @Test
    void getUserDetailsOfLoggedInUserNotFound() throws JsonProcessingException {
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Object> map = new HashMap<>();
            map.put("id", "123");
            list.add(map);
            Mockito.when(userDetails.getUserDetails()).thenThrow(JsonProcessingException.class);
            Assertions.assertThrows(RunTimeException.class, () -> userFormDataController.getUserDetailsOfLoggedInUser());
    }
    @Test
    void updateUserDetailsOfLoggedInUserSuccess()  {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,"1","abc");
        userFormDataController.updateUserDetailsOfLoggedInUser(userFormDataSchema);
        Mockito.verify(userFormDataService,Mockito.times(1)).saveUserFormData(userFormDataSchema);
    }
    @Test
    void updateUserDetailsOfLoggedInUserFailureException()  {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,"1","abc");
        Mockito.when(userFormDataService.saveUserFormData(any())).thenThrow(RuntimeException.class);
        Assertions.assertThrows(RuntimeException.class,()->userFormDataController.updateUserDetailsOfLoggedInUser(userFormDataSchema));
    }
    @Test
    void saveUser()
    {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,"1","abc");
        ApiResponse<UserFormDataSchema> response = userFormDataController.saveUser(userFormDataSchema,httpHeaders);
        Assertions.assertNotNull(response);
    }

    @Test
    void getUserByUserId()
    {
        ApiResponse<AuditableData> response = userFormDataController.getUserByUserId("1",true);
        Assertions.assertNotNull(response);
    }

    @Test
    void getAllUsers()
    {
        userFormDataController.getAllUsers("abc",true,null,1,null,"abc","abc");
        userFormDataController.getAllUsers("abc",true,null,1,null,null,"abc");
        userFormDataController.getAllUsers("abc",true,1,1,null,null,"abc");
        ApiResponse response = userFormDataController.getAllUsers("abc",true,1,1,null,"abc","abc");
        Assertions.assertNotNull(response);
    }

    @Test
    void deleteUserByUserId()
    {
        ApiResponse response = userFormDataController.deleteUserByUserId("1");
        Assertions.assertNotNull(response);
    }
}
