package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.model.ApiResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.GET;
import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles(TEST_ACTIVE_PROFILE)
//@SpringBootTest
    @ExtendWith(MockitoExtension.class)
class SUserDetailsIdNotFoundExceptionTest
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

    @Test
    void userDetailsIdNotFoundExceptionTest() throws JsonProcessingException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        ApiResponse apiResponse=new ApiResponse(userList,true,USER_DETAILS_RETRIEVED_SUCCESS);
        Map<String,Object> response=objectMapper.convertValue(apiResponse,Map.class);
        Mockito.when(mockTokenUtils.getLoggedInUserName()).thenReturn(ABC);
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
        Mockito.when(mockWebClientWrapper.webclientRequest(any(),any(),eq(GET),any())).thenReturn
                (
                        INITIALIZATION_DATA
                );
        Mockito.when(mockObjectMapper.readValue(anyString(),(TypeReference<Map<String,Object>>) any()))
                .thenReturn(response);
//        Mockito.when(mockObjectMapper.convertValue(any(),eq(List.class))).thenReturn(userList);
        Assertions.assertThrows(UserDetailsIdNotFoundException.class, () ->
                mockUserDetails.getUserDetails());
    }
}
