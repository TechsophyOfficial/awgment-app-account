package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.CustomFilter;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.UserPreferencesControllerImplementation;
import com.techsophy.tsf.account.exception.GlobalExceptionHandler;
import com.techsophy.tsf.account.exception.ProfilePictureNotPresentException;
import com.techsophy.tsf.account.exception.UserPreferencesNotFoundByLoggedInUserIdException;
import com.techsophy.tsf.account.service.UserPreferencesThemeService;
import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.PROFILE_PICTURE_CANNOT_BE_EMPTY;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.FILE;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
class UserPreferenceControllerExceptionTest
{
    @Mock
    TokenUtils mockTokenUtils;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    WebApplicationContext webApplicationContext;
    @Autowired
    CustomFilter customFilter;
    @InjectMocks
    UserPreferencesControllerImplementation mockUserPreferencesController;
    @Mock
    GlobalMessageSource mockGlobalMessageSource;
    @Mock
    UserPreferencesThemeService mockUserPreferencesThemeService;

    MockHttpServletRequest request = new MockHttpServletRequest();
    @BeforeEach
    public void setUp()
    {
        mockMvc = MockMvcBuilders.standaloneSetup(mockUserPreferencesController)
                .setControllerAdvice(new GlobalExceptionHandler(mockGlobalMessageSource))
                .build();
        MockitoAnnotations.openMocks(this);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void getUserPreferenceThemeEntityNotFoundByIdExceptionTest() throws Exception
    {
        RequestBuilder requestBuilderTest = MockMvcRequestBuilders.get(BASE_URL + VERSION_V1 + USER_PREFERENCES_URL)
                .contentType(MediaType.APPLICATION_JSON);
        MvcResult mvcResult = this.mockMvc.perform(requestBuilderTest).andExpect(status().isOk()).andReturn();
        assertEquals(200, mvcResult.getResponse().getStatus());
    }

    @Test
    void deleteUserProfilePicturePreferencesNotFoundExceptionTest() throws Exception
    {
        Mockito.when(mockUserPreferencesController.deleteProfilePhotoByUserId()).thenThrow(new UserPreferencesNotFoundByLoggedInUserIdException(USER_PREFERENCE_SCHEMA_NOT_FOUND,USER_PREFERENCE_SCHEMA_NOT_FOUND));
        RequestBuilder requestBuilderTest = MockMvcRequestBuilders.delete(BASE_URL + VERSION_V1 + USER_PREFERENCES_URL+PROFILE_PICTURE_URL)
                .contentType(MediaType.APPLICATION_JSON);
        MvcResult mvcResult = this.mockMvc.perform(requestBuilderTest).andExpect(status().isInternalServerError()).andReturn();
        assertEquals(500,mvcResult.getResponse().getStatus());
    }

    @Test
    void uploadProfilePictureNotPresentExceptionTest() throws Exception
    {
        byte[] mockProfilePicture=new byte[]{123};
        MockMultipartFile file=new MockMultipartFile(FILE,IMAGE_PNG,MediaType.IMAGE_PNG_VALUE,PICTURE_CONTENT.getBytes());
        Mockito.when(mockUserPreferencesController.uploadProfilePictureByUserId(file)).thenThrow(new ProfilePictureNotPresentException(PROFILE_PICTURE_CANNOT_BE_EMPTY,PROFILE_PICTURE_CANNOT_BE_EMPTY));
        RequestBuilder requestBuilderTest=MockMvcRequestBuilders
                .multipart(BASE_URL+VERSION_V1+USER_PREFERENCES_URL+PROFILE_PICTURE_URL).file(file)
                .content(mockProfilePicture);
        MvcResult mvcResult = this.mockMvc.perform(requestBuilderTest).andExpect(status().isInternalServerError()).andReturn();
        assertEquals(500,mvcResult.getResponse().getStatus());
    }
}
