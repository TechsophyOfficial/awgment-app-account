package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserFormDataControllerImpl;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.util.HashMap;
import java.util.Map;

import static com.techsophy.tsf.account.constants.ThemesConstants.NULL;
import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;


@ExtendWith({MockitoExtension.class, SpringExtension.class})
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
    @ParameterizedTest
    @CsvSource({"1,1", "3,0", ",1"}) //here i am verifying with passing id and their invocation for service layer
    void updateUserDetailsOfLoggedInUserSuccessWithId(String args,int invocation) throws JsonProcessingException {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,args,"abc");
        List<Map<String,Object>> list = new ArrayList<>();
        map.put("id","1");
        list.add(map);
        Mockito.when(userDetails.getUserDetails()).thenReturn(list);
        userFormDataController.updateUserDetailsOfLoggedInUser(userFormDataSchema);
        Mockito.verify(userFormDataService,Mockito.times(invocation)).saveUserFormData(userFormDataSchema);
    }
    @Test
    void updateUserDetailsOfLoggedInUserFailureException() throws JsonProcessingException {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,"1","abc");
        Mockito.when(userDetails.getUserDetails()).thenThrow(JsonProcessingException.class);
        Assertions.assertThrows(RunTimeException.class,()->userFormDataController.updateUserDetailsOfLoggedInUser(userFormDataSchema));
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
