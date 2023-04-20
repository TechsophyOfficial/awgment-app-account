package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.impl.UserFormDataServiceImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.GET;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsIdNotFoundExceptionTest
{
    @Mock
    GlobalMessageSource mockGlobalMessageSource;
    @Mock
    UserServiceImpl userService;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    WebClient webClient;
    @Mock
    TokenUtils mockTokenUtils;

    @InjectMocks
    UserDetails mockUserDetails;


    @Test
    void userDetailsIdNotFoundExceptionTest() throws JsonProcessingException
    {
        Mockito.when(mockTokenUtils.getLoggedInUserId()).thenReturn(ABC);
        Mockito.when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(userService.getAllUsersByFilter(any(),any())).thenReturn(null);
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
        Assertions.assertThrows(InvalidInputException.class,()->mockUserDetails.getUserDetails());
    }
}
