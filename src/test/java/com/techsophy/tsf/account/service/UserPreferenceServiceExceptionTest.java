package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.config.LocaleConfig;
import com.techsophy.tsf.account.exception.EntityIdNotFoundException;
import com.techsophy.tsf.account.repository.UserPreferencesDefinitionRepository;
import com.techsophy.tsf.account.service.impl.UserPreferencesThemeServiceImplementation;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceExceptionTest
{
    @Mock
    UserDetails mockUserDetails;
    @Mock
    UserPreferencesDefinitionRepository mockUserPreferenceDefinitionRepository;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    IdGeneratorImpl mockIdGeneratorImpl;
    @Mock
    GlobalMessageSource mockGlobalMessageSource;
    @Mock
    LocaleConfig mockLocaleConfig;
    @Mock
    MultipartFile mockProfilePhoto;
    @InjectMocks
    UserPreferencesThemeServiceImplementation mockUserPreferencesThemeServiceImpl;

    List<Map<String, Object>> userList = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    @BeforeEach
    public void init()
    {
        map.put(CREATED_BY_ID, NULL);
        map.put(CREATEDBYNAME, NULL);
        map.put(CREATEDON, NULL);
        map.put(UPDATEDBYID, NULL);
        map.put(UPDATEDBYNAME, NULL);
        map.put(UPDATED_ON, NULL);
        map.put(ID, null);
        map.put(USER_NAME, TEJASWINI);
        map.put(FIRST_NAME, KAZA);
        map.put(LAST_NAME, TEJASWINI);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, TEJASWINI_MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);
    }

    @Test
    void getUserPreferenceThemeEntityIdNotFoundExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        Assertions.assertThrows(EntityIdNotFoundException.class,()-> mockUserPreferencesThemeServiceImpl.getUserPreferencesThemeByUserId());
    }

    @Test
    void uploadProfilePictureEntityNotFoundExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        Assertions.assertThrows(EntityIdNotFoundException.class,()-> mockUserPreferencesThemeServiceImpl.uploadProfilePictureByUserId(mockProfilePhoto));
    }

    @Test
    void deleteUserPreferenceThemeEntityIdNotFoundExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        Assertions.assertThrows(EntityIdNotFoundException.class,()-> mockUserPreferencesThemeServiceImpl.deleteUserPreferencesThemeByUserId());
    }

    @Test
    void deleteProfilePictureEntityIdNotFoundExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        Assertions.assertThrows(EntityIdNotFoundException.class,()-> mockUserPreferencesThemeServiceImpl.deleteProfilePictureByUserId());
    }
}
