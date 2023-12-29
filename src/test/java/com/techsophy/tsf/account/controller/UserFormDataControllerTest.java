package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserFormDataControllerImpl;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.exception.BadRequestException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import static org.mockito.ArgumentMatchers.any;

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
    @Mock
    ObjectMapper objectMapper;
    @InjectMocks
    UserFormDataControllerImpl userFormDataController;


    @Test
    void getUserDetailsOfLoggedInUserSuccess() throws JsonProcessingException {
        Mockito.when(userDetails.getUserId()).thenReturn(Optional.of(BigInteger.ONE));
        userFormDataController.getUserDetailsOfLoggedInUser();
        Mockito.verify(userFormDataService,Mockito.times(1)).getUserFormDataByUserId(String.valueOf(BigInteger.ONE),false);
    }
    @Test
    void getUserDetailsOfLoggedInUserNotFound() throws JsonProcessingException {
            Mockito.when(userDetails.getUserId()).thenReturn(Optional.empty());
            Mockito.when(tokenUtils.getLoggedInUserId()).thenReturn("abc");
            userFormDataController.getUserDetailsOfLoggedInUser();
            Mockito.verify(userFormDataService,Mockito.times(1)).getUserFormData("abc");
    }
    @ParameterizedTest
    @CsvSource({"1,1", ",1"}) //here i am verifying with passing id and their invocation for service layer
    void updateUserDetailsOfLoggedInUserSuccessWithId(String args,int invocation) throws JsonProcessingException {
        Map<String,Object> inputData = new HashMap<>();
        inputData.put("userName","nandini");
        inputData.put("emailId","nandini.k@techsophy.com");
        inputData.put("groups","awgment");
        inputData.put("roles","awgment-account-all");
        Map<String,Object> exitData = new HashMap<>();
        exitData.put("userName","vaibhav");
        exitData.put("emailId","vaibhav.j@techsophy.com");
        exitData.put("groups","awgment-all");
        exitData.put("roles","awgment-account-all");
        exitData.put("id","1");
        UserFormDataSchema userFormDataSchemaInputData = new UserFormDataSchema(inputData,args,"abc");
        UserFormDataSchema userFormDataSchemaExitData = new UserFormDataSchema(exitData,args,"abc");
        List<Map<String,Object>> list = new ArrayList<>();
        inputData.put("id","1");
        list.add(inputData);
        AuditableData auditableData = new AuditableData("abc", Instant.now(),"abc",Instant.now());
        Mockito.when(userFormDataService.getUserFormDataByUserId("1",false)).thenReturn(auditableData);
        Mockito.when(objectMapper.convertValue(any(), ArgumentMatchers.eq(UserFormDataSchema.class))).thenReturn(userFormDataSchemaExitData);
        Mockito.when(userDetails.getUserDetails()).thenReturn(list);
        Mockito.when(userFormDataService.saveUserFormData(userFormDataSchemaInputData)).thenReturn(userFormDataSchemaInputData);
        ApiResponse<UserFormDataSchema> userFormDataSchemaApiResponse = userFormDataController.updateUserDetailsOfLoggedInUser(userFormDataSchemaInputData);
        Assertions.assertEquals(userFormDataSchemaExitData.getUserData().get("userName"),userFormDataSchemaApiResponse.getData().getUserData().get("userName"));
        Assertions.assertEquals(userFormDataSchemaExitData.getUserData().get("emailId"),userFormDataSchemaApiResponse.getData().getUserData().get("emailId"));
        Assertions.assertEquals(userFormDataSchemaExitData.getUserData().get("groups"),userFormDataSchemaApiResponse.getData().getUserData().get("groups"));
        Assertions.assertEquals(userFormDataSchemaExitData.getUserData().get("roles"),userFormDataSchemaApiResponse.getData().getUserData().get("roles"));
        Assertions.assertEquals("1",userFormDataSchemaApiResponse.getData().getUserId());
        Mockito.verify(userFormDataService,Mockito.times(invocation)).saveUserFormData(userFormDataSchemaInputData);
    }
    @Test
    void updateUserDetailsOfLoggedInUserMisMatch() throws JsonProcessingException {
        Map<String,Object> map = new HashMap<>();
        map.put("userName","nandini");
        map.put("emailId","nandini.k@techsophy.com");
        map.put("groups","awgment");
        map.put("roles","awgment-account-all");
        AuditableData auditableData = new AuditableData("abc", Instant.now(),"abc",Instant.now());
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,"1","abc");
        Mockito.when(userFormDataService.getUserFormDataByUserId("1",false)).thenReturn(auditableData);
        Mockito.when(objectMapper.convertValue(any(), ArgumentMatchers.eq(UserFormDataSchema.class))).thenReturn(userFormDataSchema);
        List<Map<String,Object>> list = new ArrayList<>();
        map.put("id","2");
        list.add(map);
        Mockito.when(userDetails.getUserDetails()).thenReturn(list);
        Assertions.assertThrows(BadRequestException.class,()->userFormDataController.updateUserDetailsOfLoggedInUser(userFormDataSchema));
    }
    @Test
    void updateUserDetailsOfLoggedInUserFailureException() throws JsonProcessingException {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(map,"1","abc");
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

    @Test
    void getUsersRegisteredToday()
    {
        ApiResponse<List<UserFormDataDefinition>> response = userFormDataController.getUsersRegisteredInDay();
        Assertions.assertNotNull(response);
    }
}
