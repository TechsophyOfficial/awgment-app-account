package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserFormDataControllerImpl;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.util.HashMap;
import java.util.Map;

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
