package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.ThemesConstants;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.model.ApiResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.CREATED_ON;
import static com.techsophy.tsf.account.constants.AccountConstants.GET;
import static com.techsophy.tsf.account.constants.ThemesConstants.*;
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
import static com.techsophy.tsf.account.constants.UserConstants.ID;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;

@ActiveProfiles(TEST_ACTIVE_PROFILE)
//@SpringBootTest
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
        ObjectMapper objectMapper=new ObjectMapper();
        ApiResponse apiResponse=new ApiResponse(userList,true,USER_DETAILS_RETRIEVED_SUCCESS);
        Map<String,Object> response=objectMapper.convertValue(apiResponse,Map.class);
        Mockito.when(mockTokenUtils.getLoggedInUserId()).thenReturn(ABC);
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
        Mockito.when(mockWebClientWrapper.webclientRequest(any(),any(),eq(GET),any())).thenReturn
                (
                        INITIALIZATION_DATA
                );
        Mockito.when(mockObjectMapper.readValue(anyString(),(TypeReference<Map<String,Object>>) any()))
                .thenReturn(response);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(List.class))).thenReturn(userList);
        Assertions.assertNotNull(mockUserDetails.getUserDetails());
    }

    @Test
    void InvalidInputExceptionTest() throws JsonProcessingException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        ApiResponse apiResponse=new ApiResponse(userList,true,USER_DETAILS_RETRIEVED_SUCCESS);
        Map<String,Object> response=objectMapper.convertValue(apiResponse,Map.class);
        Mockito.when(mockTokenUtils.getLoggedInUserId()).thenReturn(null);
//        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
//        Mockito.when(mockWebClientWrapper.webclientRequest(any(),any(),eq(GET),any())).thenReturn
//                (
//                        INITIALIZATION_DATA
//                );
//        Mockito.when(mockObjectMapper.readValue(anyString(),(TypeReference<Map<String,Object>>) any()))
//                .thenReturn(response);
        //Mockito.when(mockObjectMapper.convertValue(any(),eq(List.class))).thenReturn(userList);
        Assertions.assertThrows(InvalidInputException.class, () ->
                mockUserDetails.getUserDetails());
    }
}

