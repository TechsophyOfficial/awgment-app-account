package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.UserPreferencesResponse;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.entity.UserPreferencesDefinition;
import com.techsophy.tsf.account.exception.InvalidProfilePictureException;
import com.techsophy.tsf.account.exception.ProfilePictureNotPresentException;
import com.techsophy.tsf.account.exception.UserPreferencesNotFoundByLoggedInUserIdException;
import com.techsophy.tsf.account.repository.UserPreferencesDefinitionRepository;
import com.techsophy.tsf.account.service.impl.UserPreferencesThemeServiceImplementation;
import com.techsophy.tsf.account.utils.UserDetails;
import org.bson.types.Binary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest
{
    @Mock
    UserPreferencesDefinitionRepository mockUserPreferencesDefinitionRepository;
    @Mock
    UserDetails mockUserDetails;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    IdGeneratorImpl mockIdGeneratorImpl;
    @Mock
    GlobalMessageSource mockGlobalMessageSource;
    @Mock
    UserPreferencesDefinition userPreferenceData;
    @InjectMocks
    UserPreferencesThemeServiceImplementation mockUserPreferencesThemeServiceImplementation;

    List<Map<String, Object>> userList = new ArrayList<>();

    @BeforeEach
    public void init()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(CREATED_BY_ID, NULL);
        map.put(CREATEDBYNAME, NULL);
        map.put(CREATEDON, NULL);
        map.put(UPDATEDBYID, NULL);
        map.put(UPDATEDBYNAME, NULL);
        map.put(UPDATED_ON, NULL);
        map.put(ID, ID_NUMBER);
        map.put(USER_NAME, TEJASWINI);
        map.put(FIRST_NAME, KAZA);
        map.put(LAST_NAME, TEJASWINI);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, TEJASWINI_MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);
    }

    @Test
    void getUserPreferencesThemeByUserIdTest() throws IOException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        UserPreferencesDefinition userPreferencesDefinitionTest=new UserPreferencesDefinition();
        userPreferencesDefinitionTest.setUserId(BigInteger.valueOf(1));
        userPreferencesDefinitionTest.setId(BigInteger.valueOf(1));
        userPreferencesDefinitionTest.setThemeId(BigInteger.valueOf(1));
        byte[] data=new byte[]{Byte.parseByte(ONE)};
        userPreferencesDefinitionTest.setProfilePicture(new Binary(data));
        when(mockUserPreferencesDefinitionRepository.findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(Optional.of(userPreferencesDefinitionTest));
        mockUserPreferencesThemeServiceImplementation.getUserPreferencesThemeByUserId();
        Mockito.verify(mockUserPreferencesDefinitionRepository,Mockito.times(1)).findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)));
    }

    @Test
    void uploadProfilePictureByUserIdTest() throws IOException
    {
        UserPreferencesDefinition userPreferencesDefinitionTest=new UserPreferencesDefinition();
        userPreferencesDefinitionTest.setUserId(BigInteger.valueOf(1));
        userPreferencesDefinitionTest.setId(BigInteger.valueOf(1));
        userPreferencesDefinitionTest.setThemeId(BigInteger.valueOf(1));
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        MockMultipartFile mockProfilePicture = new MockMultipartFile(PROFILE_PICTURE_NAME,PROFILE_PICTURE_JPEG, MediaType.IMAGE_JPEG_VALUE, PICTURE_CONTENT.getBytes());
        when(mockUserPreferencesDefinitionRepository.existsByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(true);
        when(mockUserPreferencesDefinitionRepository.findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(Optional.of(userPreferencesDefinitionTest));
        mockUserPreferencesThemeServiceImplementation.uploadProfilePictureByUserId(mockProfilePicture);
        Mockito.verify(mockUserPreferencesDefinitionRepository,Mockito.times(1)).findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)));
    }

    @Test
    void deleteProfilePictureByUserIdTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        UserPreferencesDefinition userPreferencesDefinitionTest=new UserPreferencesDefinition();
        userPreferencesDefinitionTest.setUserId(BigInteger.valueOf(1));
        userPreferencesDefinitionTest.setId(BigInteger.valueOf(1));
        userPreferencesDefinitionTest.setThemeId(BigInteger.valueOf(1));
        byte[] data=new byte[]{Byte.parseByte(ONE)};
        userPreferencesDefinitionTest.setProfilePicture(new Binary(data));
        when(mockUserPreferencesDefinitionRepository.findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(Optional.of(userPreferencesDefinitionTest));
        mockUserPreferencesThemeServiceImplementation.deleteProfilePictureByUserId();
        Mockito.verify(mockUserPreferencesDefinitionRepository,Mockito.times(1)).findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)));
    }


    @Test
    void uploadProfilePictureInvalidExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        MultipartFile mockMultipartFile=new MockMultipartFile(FILE_NAME,EMPTY_STRING,EMPTY_STRING,new byte[]{Byte.parseByte(ZERO)});
        Assertions.assertThrows(InvalidProfilePictureException.class, () ->
                mockUserPreferencesThemeServiceImplementation.uploadProfilePictureByUserId(mockMultipartFile));
    }

    @Test
    void uploadProfilePictureAbsentExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        MultipartFile mockMultipartFile=new MockMultipartFile(ABC,ABC_JPEG,MediaType.IMAGE_JPEG_VALUE,new byte[]{});
        Assertions.assertThrows(ProfilePictureNotPresentException.class, () ->
                mockUserPreferencesThemeServiceImplementation.uploadProfilePictureByUserId(mockMultipartFile));
    }

    @Test
    void uploadProfilePictureUserPreferencesNotFoundByLoggedInUserIdExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        MultipartFile mockMultipartFile=new MockMultipartFile(ABC,ABC_JPEG,MediaType.IMAGE_JPEG_VALUE,new byte[]{Byte.parseByte(IMAGE_CONTENT)});
        when(mockUserPreferencesDefinitionRepository.existsByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(true);
        when(mockUserPreferencesDefinitionRepository.findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(Optional.empty());
        Assertions.assertThrows(UserPreferencesNotFoundByLoggedInUserIdException.class,()->
                mockUserPreferencesThemeServiceImplementation.uploadProfilePictureByUserId(mockMultipartFile));
    }

    @Test
    void deleteProfilePictureNotFoundByLoggedInUserIdExceptionTest() throws JsonProcessingException
    {
        when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        Assertions.assertThrows(UserPreferencesNotFoundByLoggedInUserIdException.class,()->
                mockUserPreferencesThemeServiceImplementation.deleteUserPreferencesThemeByUserId());
    }
    @Test
    void saveUserPreferenceTheme() throws Exception
    {
        UserPreferencesResponse userPreferencesResponse = new UserPreferencesResponse("1","1","1");
        UserPreferencesDefinition userPreferencesDefinition = new UserPreferencesDefinition(BigInteger.ONE,BigInteger.ONE,BigInteger.ONE,null);
        UserPreferencesSchema userPreferencesSchema = new UserPreferencesSchema("1","1","1","abc");
        UserPreferencesSchema userPreferencesSchema1 = new UserPreferencesSchema(null,"1","1","abc");
        when(mockUserDetails.getUserDetails()).thenReturn(userList);
        when(mockUserPreferencesDefinitionRepository.existsByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockUserPreferencesDefinitionRepository.save(userPreferencesDefinition)).thenReturn(userPreferencesDefinition);
        when(mockUserPreferencesDefinitionRepository.findByUserId(BigInteger.valueOf(Long.parseLong(USER_ID)))).thenReturn(Optional.of(userPreferencesDefinition));
        when(mockObjectMapper.convertValue(any(),eq(UserPreferencesResponse.class))).thenReturn(userPreferencesResponse);
        mockUserPreferencesThemeServiceImplementation.saveUserPreferencesTheme(userPreferencesSchema);
        UserPreferencesResponse response = mockUserPreferencesThemeServiceImplementation.saveUserPreferencesTheme(userPreferencesSchema1);
        Assertions.assertNotNull(response);
    }
}

