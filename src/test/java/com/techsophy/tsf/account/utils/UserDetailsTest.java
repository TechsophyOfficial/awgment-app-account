package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.ThemesConstants;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.commons.user.UserDetailsNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.*;

import static com.techsophy.tsf.account.constants.AccountConstants.CREATED_ON;
import static com.techsophy.tsf.account.constants.AccountConstants.GET;
import static com.techsophy.tsf.account.constants.ThemesConstants.CREATED_BY_ID;
import static com.techsophy.tsf.account.constants.ThemesConstants.DEPARTMENT;
import static com.techsophy.tsf.account.constants.ThemesConstants.EMAIL_ID;
import static com.techsophy.tsf.account.constants.ThemesConstants.FIRST_NAME;
import static com.techsophy.tsf.account.constants.ThemesConstants.LAST_NAME;
import static com.techsophy.tsf.account.constants.ThemesConstants.MOBILE_NUMBER;
import static com.techsophy.tsf.account.constants.ThemesConstants.NULL;
import static com.techsophy.tsf.account.constants.ThemesConstants.NUMBER;
import static com.techsophy.tsf.account.constants.ThemesConstants.UPDATEDBYID;
import static com.techsophy.tsf.account.constants.ThemesConstants.UPDATEDBYNAME;
import static com.techsophy.tsf.account.constants.ThemesConstants.USER_NAME;
import static com.techsophy.tsf.account.constants.ThemesConstants.*;
import static com.techsophy.tsf.account.constants.UserConstants.ID;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsTest
{
    @Mock
    GlobalMessageSource mockGlobalMessageSource;
    @Mock
    TokenUtils mockTokenUtils;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    WebClientWrapper mockWebClientWrapper;
    @Mock
    UserServiceImpl userService;
    @InjectMocks
    UserDetails mockUserDetails;

    List<Map<String, Object>> userList = new ArrayList<>();

    @BeforeEach
    public void init()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(CREATED_BY_ID, NULL);
        map.put(ThemesConstants.CREATEDBYNAME, NULL);
        map.put(CREATED_ON, NULL);
        map.put(UPDATEDBYID, NULL);
        map.put(UPDATEDBYNAME, NULL);
        map.put(UPDATEDON, NULL);
        map.put(ID, BIGINTEGER_ID);
        map.put("id", BigInteger.ONE);
        map.put(USER_NAME, USER_FIRST_NAME);
        map.put(FIRST_NAME, USER_LAST_NAME);
        map.put(LAST_NAME, USER_FIRST_NAME);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);
    }

    @Test
    void getUserDetailsTest() throws JsonProcessingException
    {
        AuditableData auditableData = new AuditableData();
        ObjectMapper objectMapper=new ObjectMapper();
        ApiResponse apiResponse=new ApiResponse(userList,true,USER_DETAILS_RETRIEVED_SUCCESS);
        Map<String,Object> response=objectMapper.convertValue(apiResponse,Map.class);
        Mockito.when(mockTokenUtils.getLoggedInUserId()).thenReturn(ABC);
        Mockito.when(userService.getAllUsersByFilter(any(),any())).thenReturn(List.of(auditableData));
        Mockito.when(mockObjectMapper.convertValue(any(),eq(List.class))).thenReturn(userList);
        mockUserDetails.getUserDetails();
        verify(userService,times(1)).getAllUsersByFilter(any(),any());

    }


    @Test
    void InvalidInputExceptionTest()
    {
        Mockito.when(mockTokenUtils.getLoggedInUserId()).thenReturn(null);
        Assertions.assertThrows(InvalidInputException.class, () ->
                mockUserDetails.getUserDetails());
    }
    @Test
    void getCurrentAuditorException() throws JsonProcessingException {
        ObjectMapper objectMapper=new ObjectMapper();
        ApiResponse apiResponse=new ApiResponse(userList,true,USER_DETAILS_RETRIEVED_SUCCESS);
        Map<String,Object> response=objectMapper.convertValue(apiResponse,Map.class);
        List<Map<String, Object>> userListData = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put(ID, null);
        userListData.add(map);
        Optional<BigInteger> id =  mockUserDetails.getCurrentAuditor();
        Assertions.assertEquals(Optional.empty(),id);
    }
    @Test
    void getCurrentAuditorSuccess() throws JsonProcessingException {
        ObjectMapper objectMapper=new ObjectMapper();
        ApiResponse apiResponse=new ApiResponse(userList,true,USER_DETAILS_RETRIEVED_SUCCESS);
        Map<String,Object> response=objectMapper.convertValue(apiResponse,Map.class);
        Mockito.when(mockTokenUtils.getLoggedInUserId()).thenReturn(ABC);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(List.class))).thenReturn(userList);
        Optional<BigInteger> id =  mockUserDetails.getCurrentAuditor();
        Assertions.assertEquals(Optional.of(BigInteger.ONE),id);
    }
}

