package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.config.TokenConfig;
import com.techsophy.tsf.account.constants.AccountConstants;
import com.techsophy.tsf.account.constants.ThemesConstants;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.exception.ThemesNotFoundByIdException;
import com.techsophy.tsf.account.repository.ThemesDefinitionRepository;
import com.techsophy.tsf.account.service.impl.ThemesServiceImplementation;
import com.techsophy.tsf.account.service.impl.UserManagementInKeyCloakImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.Cleanup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.ThemesConstants.*;
import static com.techsophy.tsf.account.constants.UserConstants.NAME;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.FILE;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.FILE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@ExtendWith(MockitoExtension.class)
class ThemesServiceTest
{
    @Mock
    UserDetails mockUserDetails;
    @Autowired
    UserManagementInKeyCloakImpl userManagementInKeyCloak;
    @Mock
    TokenConfig tokenConfig;
    @Mock
    ThemesDefinitionRepository themesDefinitionRepository;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    ObjectMapper mockObjectMapper;
    @InjectMocks
    ThemesServiceImplementation themesServiceImplementation;
    @Mock
   IdGeneratorImpl idGenerator;
    @Mock
    TokenUtils accountUtils;
    private static final String THEMES_DATA = "testdata/themes-schema.json";
    private  final String DOC_DOWNLOAD_DATA = "testdata/theme1.json";

    List<Map<String, Object>> userList = new ArrayList<>();

    @BeforeEach
    public void init()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(CREATEDBYID, NULL);
        map.put(CREATEDBYNAME, NULL);
        map.put(CREATEDON, NULL);
        map.put(UPDATEDBYID, NULL);
        map.put(UPDATEDBYNAME, NULL);
        map.put(UPDATEDON, NULL);
        map.put(ID_KEY, BIGINTEGER_ID);
        map.put(USER_NAME, USER_FIRST_NAME);
        map.put(FIRST_NAME, USER_LAST_NAME);
        map.put(LAST_NAME, USER_FIRST_NAME);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);
    }

    @Test
    void saveThemesDataTest() throws IOException
    {
        Mockito.when(mockUserDetails.getUserDetails()).thenReturn(userList);
        ObjectMapper objectMapper = new ObjectMapper();
        @Cleanup InputStream stream = new ClassPathResource(THEMES_DATA).getInputStream();
        String themeData = new String(stream.readAllBytes());
        ThemesSchema themesSchema = objectMapper.readValue(themeData, ThemesSchema.class);
        ThemesSchema themesSchema1 = new ThemesSchema(null,NAME,CONTENT);
        ThemesDefinition themesDefinition = objectMapper.readValue(themeData, ThemesDefinition.class);
        when(themesDefinitionRepository.save(any())).thenReturn(themesDefinition.withId(BigInteger.valueOf(Long.parseLong(ThemesConstants.ID))));
        when(mockObjectMapper.convertValue(any(),eq(ThemesResponse.class ))).thenReturn(new ThemesResponse(ThemesConstants.ID));
        when(themesDefinitionRepository.findById((BigInteger) any())).thenReturn(Optional.of(new ThemesDefinition(BigInteger.valueOf(Long.parseLong(ID)), NAME, CONTENT)));
        themesServiceImplementation.saveThemesData(themesSchema);
        themesServiceImplementation.saveThemesData(themesSchema1);
        verify(themesDefinitionRepository,times(2)).save(any());
    }

    @Test
    void saveThemesDataDuplicateNameTest() throws IOException
    {
        ThemesSchema themesSchema=new ThemesSchema("101","theme_violet","violet_content");
        Mockito.when(themesDefinitionRepository.existsByName("theme_violet")).thenReturn(true);
        Assertions.assertThrows(BadRequestException.class,()->themesServiceImplementation.saveThemesData(themesSchema));
    }

    @Test
    void getThemesDataByIdTest() throws IOException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        @Cleanup InputStream stream = new ClassPathResource(THEMES_DATA).getInputStream();
        String themesData= new String(stream.readAllBytes());
        ThemesResponseSchema themesResponseSchema= objectMapper.readValue(themesData,ThemesResponseSchema.class);
        ThemesDefinition themesDefinition=objectMapper.readValue(themesData,ThemesDefinition.class);
        when(mockObjectMapper.convertValue(any(),eq(ThemesResponseSchema.class))).thenReturn(themesResponseSchema);
        when(themesDefinitionRepository.findById((BigInteger) any())).thenReturn(java.util.Optional.ofNullable(themesDefinition));
        ThemesResponseSchema response=themesServiceImplementation.getThemesDataById("1");
        assertThat(response).isEqualTo(themesResponseSchema);
    }

    @Test
    void getAllThemesDataTest() throws IOException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        @Cleanup InputStream stream = new ClassPathResource(THEMES_DATA).getInputStream();
        String themesData= new String(stream.readAllBytes());
        ThemesResponseSchema themesResponseSchema = objectMapper.readValue(themesData,ThemesResponseSchema.class);
        ThemesDefinition themesDefinition=objectMapper.readValue(themesData,ThemesDefinition.class);
        when(mockObjectMapper.convertValue(any(),eq(ThemesResponseSchema.class))).thenReturn(themesResponseSchema);
        when(themesDefinitionRepository.findAll((Sort) any())).thenReturn(List.of(themesDefinition));
        when(themesDefinitionRepository.findByIdIn(any())).thenReturn(List.of(themesDefinition));
        when(themesDefinitionRepository.findThemesByQSorting(anyString(),any())).thenReturn(Stream.of(themesDefinition));
        themesServiceImplementation.getAllThemesData("abc",null ,null );
        themesServiceImplementation.getAllThemesData(null,"abc" ,null );
        Stream<ThemesResponseSchema> response=themesServiceImplementation.getAllThemesData(null,null ,null );
        assertThat(response).isEqualTo(List.of(themesResponseSchema));
    }

    @Test
    void getAllThemesData() throws Exception
    {
        Pageable pageable = PageRequest.of(1,1);
        ObjectMapper objectMapper=new ObjectMapper();
        @Cleanup InputStream stream = new ClassPathResource(THEMES_DATA).getInputStream();
        String themesData= new String(stream.readAllBytes());
        ThemesDefinition themesDefinition=objectMapper.readValue(themesData,ThemesDefinition.class);
        Page<ThemesDefinition> page = new PageImpl<>(List.of(themesDefinition));
        when(themesDefinitionRepository.findThemesByQPageable(anyString(),any())).thenReturn(page);
        when(themesDefinitionRepository.findAll(pageable)).thenReturn(page);
        PaginationResponsePayload paginationResponsePayload = new PaginationResponsePayload();
        when(accountUtils.getPaginationResponsePayload(any(),any())).thenReturn(paginationResponsePayload);
        themesServiceImplementation.getAllThemesData("abc",pageable);
        themesServiceImplementation.getAllThemesData("",pageable);
        verify(themesDefinitionRepository,times(1)).findAll(pageable);
    }

    @Test
    void deleteThemesDataByIdTest()
    {
        when(themesDefinitionRepository.existsById(BigInteger.valueOf(1))).thenReturn(true).thenReturn(false);
        doNothing().when(themesDefinitionRepository).deleteById(BigInteger.valueOf(1));
        themesServiceImplementation.deleteThemesDataById("1");
        Assertions.assertThrows( ThemesNotFoundByIdException.class,()->themesServiceImplementation.deleteThemesDataById("1"));
        verify(themesDefinitionRepository, times(1)).deleteById(BigInteger.valueOf(1));
    }

    @Test
    void downloadThemeByIdTest() throws IOException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        @Cleanup InputStream stream = new ClassPathResource(DOC_DOWNLOAD_DATA).getInputStream();
        String id="866660468878082061";
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("foo", "bar");
        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list.add(map1);
        String json = objectMapper.writeValueAsString(list);
        String responseString=new String(stream.readAllBytes());
        Map<String, Object> response = objectMapper.readValue(responseString, new TypeReference<>() {});
        Map<String, Object> exportResponse = objectMapper.convertValue(response.get("data"), Map.class);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(json).thenReturn(json);
        when(accountUtils.getTokenFromContext()).thenReturn("abc");
        when(webClientWrapper.createWebClient(any())).thenReturn(WebClient.builder().build());
        when(mockObjectMapper.readValue(anyString(), (TypeReference<Map<String,Object>>) any())).thenReturn(response);
        when(mockObjectMapper.convertValue(any(),eq(Map.class))).thenReturn(exportResponse);
        ResponseEntity<Resource> responseEntity= themesServiceImplementation.downloadTheme(id);
        Assertions.assertNotNull(responseEntity);
    }

    @Test
    void uploadThemeTest() throws IOException
    {
        MockMultipartFile file = new MockMultipartFile(FILE_NAME,FILE, TEXT_PLAIN_VALUE,"abc".getBytes());
        UploadSchema uploadSchema = new UploadSchema(ID,NAME,CONTENT);
        UploadSchema uploadSchema1 = new UploadSchema(null,NAME,CONTENT);
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.valueOf(1),NAME,CONTENT);
        when(mockUserDetails.getUserDetails()).thenReturn(userList);
        when(this.mockObjectMapper.readValue(anyString(),eq(UploadSchema.class))).thenReturn(uploadSchema).thenReturn(uploadSchema1);
        when(this.mockObjectMapper.convertValue(any(),eq(ThemesDefinition.class))).thenReturn(themesDefinition);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(1));
        when(themesDefinitionRepository.findById(BigInteger.valueOf(Long.parseLong("1")))).thenReturn(Optional.of(themesDefinition));
        when(this.themesDefinitionRepository.save(themesDefinition)).thenReturn(themesDefinition);
        themesServiceImplementation.uploadTheme(file,"name");
        themesServiceImplementation.uploadTheme(file,"name");
        verify(themesDefinitionRepository, times(2)).save(themesDefinition);
    }

    @Test
    void uploadThemeDuplicateNameTest()
    {
        MockMultipartFile file = new MockMultipartFile("theme_violet",FILE, "json","abc".getBytes());
        Mockito.when(themesDefinitionRepository.existsByName("theme_violet")).thenReturn(true);
        Assertions.assertThrows(BadRequestException.class,()->themesServiceImplementation.uploadTheme(file,"theme_violet"));
    }
}

