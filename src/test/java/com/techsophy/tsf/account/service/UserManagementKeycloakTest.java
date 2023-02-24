package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.AccountConstants;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.exception.MailException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.exception.UserNotFoundException;
import com.techsophy.tsf.account.service.impl.UserManagementInKeyCloakImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.GroupsDataServiceConstants.RESPONSE;
import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

//@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles(TEST_ACTIVE_PROFILE)
//@ExtendWith({SpringExtension.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
 class UserManagementKeycloakTest {
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    TokenUtils mockTokenUtils;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    UserServiceImpl mockUserServiceImpl;
    @Mock
    GlobalMessageSource globalMessageSource;
    @InjectMocks
    UserManagementInKeyCloakImpl userManagementInKeyCloak;
    private static final String USER_DATA = "testdata/user-data.json";
    String response;
    WebClient webClient;
    HashMap<String, String> hashMap = new HashMap<>();
    List<Map<String, Object>> list1 = new ArrayList<>();
    Map<String, Object> map1 = new HashMap<>();
    @BeforeEach
    void  init() {
        response = RESPONSE;
        webClient = WebClient.builder().build();
        hashMap.put("id", "value");
        hashMap.put("userName", "value");
        map1.put("userName", "nandini");
        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list1.add(map1);
//        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        ReflectionTestUtils.setField(userManagementInKeyCloak,"requiredClientsCSV","camunda-identity-service,ticketing-system");
    }
//    @Test
//    void createUserTest() throws Exception
//    {
//        String response=RESPONSE;
//        ObjectMapper mapper = new ObjectMapper();
//        Map<String, Object> userDataId = new HashMap<String,Object>();
//        userDataId.put("userName", "Bhavya");
//        userDataId.put("id","1");
//        Map<String, Object> userData = new HashMap<String,Object>();
//        userData.put("userName", "Bhavya");
//        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
//        Map<String, Object> map1 = new HashMap<String, Object>();
//        map1.put("foo", "bar");
//        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
//        list.add(map1);
//        String json = mapper.writeValueAsString(list);
//        ObjectMapper objectMap1 = new ObjectMapper();
//        InputStream stream = new ClassPathResource(USER_DATA).getInputStream();
//        UserDataSchema userDataSchema = objectMap1.readValue(new String(stream.readAllBytes()), UserDataSchema.class);
//        UserSchema userSchema = objectMap1.convertValue(userDataSchema.getUserData(), UserSchema.class);
//        UserDataSchema userDataSchema1 = new UserDataSchema(userDataId, null);
//        UserDataSchema userDataSchema2 = new UserDataSchema(userData, "1");
//        UserSchema userSchemaIm = new UserSchema("1","abc","abc","abc","abc","abc","abc");
//        Mockito.when(mockObjectMapper.convertValue(any(), ArgumentMatchers.eq(UserSchema.class))).thenReturn(userSchemaIm);
//        WebClient webClient= WebClient.builder().build();
//        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
//        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(json).thenReturn(json);
//        Map<String, Object> map = new HashMap<String, Object>();
//        map.put("foo", "bar");
//        Mockito.when(mockObjectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(map).thenReturn(List.of(new HashMap<String,Object>()));
//        when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
//        Map<String, Object> response1 = userManagementInKeyCloak.createUser(userDataSchema);
//        userManagementInKeyCloak.createUser(userDataSchema1);
//        userManagementInKeyCloak.createUser(userDataSchema2);
//        Assertions.assertNotNull(response1);
//    }

//    @Test
//    void assignUserRolesTest() throws Exception
//    {
//        ObjectMapper mapper = new ObjectMapper();
//        InputStream stream = new ClassPathResource(USER_DATA).getInputStream();
//        String tenantData = new String(stream.readAllBytes());
//        Map<String, Object> tenantDetails = mapper.readValue(tenantData, Map.class);
//        UserRolesSchema userRolesSchema = new UserRolesSchema("1",List.of());
//        RolesSchema rolesSchema = new RolesSchema("1","abc");
//        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
//        Map<String, Object> map1 = new HashMap<>();
//        map1.put("foo", "bar");
//        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
//        list.add(map1);
//        Map<String, Object> map = new HashMap<String, Object>();
//        map.put("foo", "bar");
//        String json = mapper.writeValueAsString(list);
//        WebClient webClient= WebClient.builder().build();
//        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
//        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(json);
//        Mockito.when(mockObjectMapper.readValue(json,List.class)).thenReturn( list);
//        Mockito.when(mockObjectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(map).thenReturn(map).thenReturn(List.of(map));
//        Mockito.when(mockObjectMapper.convertValue(any(), ArgumentMatchers.eq(RolesSchema.class))).thenReturn(rolesSchema);        // when(userManagementInKeyCloak.getUserRoles("token","1")).thenReturn(Collections.singletonList(rolesSchema));
//        when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
//        userManagementInKeyCloak.assignUserRole(userRolesSchema);
//        verify(mockTokenUtils,times(2)).getTokenFromContext();
//    }
    @Test
    void createGroupTest() throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        GroupsSchema groupsSchema = new GroupsSchema("1","abc");
        GroupsSchema groupsSchema1 = new GroupsSchema(null,"abc");
        WebClient webClient= WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        SaveSchema  saveSchema = new SaveSchema("abc");
        GroupsSaveSchema groupsSaveSchema = new GroupsSaveSchema("1","abc");
        List<Map<String, Object>> list1 = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list1.add(map);
//        Mockito.when(mockObjectMapper.readValue(anyString(),ArgumentMatchers.eq(SaveSchema.class))).thenReturn(saveSchema);
        Mockito.when(mockObjectMapper.convertValue(any(),ArgumentMatchers.eq(GroupsSaveSchema.class))).thenReturn(groupsSaveSchema);
        Mockito.when(mockObjectMapper.convertValue(any(),ArgumentMatchers.eq(SaveSchema.class))).thenReturn(saveSchema);
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("foo", "bar");
        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list.add(map1);
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        String json = objectMapper.writeValueAsString(list);
        Mockito.when(mockObjectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(map).thenReturn(list1).thenReturn(map).thenReturn(list1).thenReturn(map).thenReturn(list1)
                        .thenReturn(map).thenReturn(list1);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(json);
//        Mockito.when(mockObjectMapper.readValue(json,List.class)).thenReturn( list);
        userManagementInKeyCloak.createGroup(groupsSchema);
        List<GroupsSaveSchema>  response = userManagementInKeyCloak.createGroup(groupsSchema1);
        Assertions.assertNotNull(response);
    }
    @Test
    void getGroupByIdTest() throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        GroupsSchema groupsSchema = new GroupsSchema("1","abc");
        WebClient webClient= WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("foo", "bar");
        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list.add(map1);
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        String json = objectMapper.writeValueAsString(list);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(json);
        Mockito.when(mockObjectMapper.readValue(anyString(),(TypeReference<HashMap<String, Object>>) any()
        )).thenReturn((HashMap<String, Object>) map1);
        Mockito.when(mockObjectMapper.convertValue(any(),ArgumentMatchers.eq(GroupsSchema.class))).thenReturn(groupsSchema);
        GroupsSchema response = userManagementInKeyCloak.getGroupById("1");
        Assertions.assertNotNull(response);
    }
    @Test
    void assignUserGroupTest() throws Exception
    {
        String response = RESPONSE;
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream stream = new ClassPathResource(USER_DATA).getInputStream();
        String tenantData = new String(stream.readAllBytes());
        Map<String, Object> tenantDetails = objectMapper.readValue(tenantData, Map.class);
        List<String> list = new ArrayList<>();
        list.add("abc");
        UserGroupsSchema userGroupsSchema = new UserGroupsSchema("abc",list);
        WebClient webClient= WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Map<String,Object> map2 = new HashMap<>();
        map2.put("abc","abc");
        List<Map<String, Object>> list1 = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        HashMap<String, Object> map = new HashMap<>();
        map1.put("id", "1");
        map1.put("name","abc");
        map.put("id", "1");
        map.put("name","abc");
        list1.add(map1);
        String json = objectMapper.writeValueAsString(list);
        UserRolesSchema userRolesSchema = new UserRolesSchema("1",list);
        GroupsSaveSchema groupsSaveSchema = new GroupsSaveSchema("1","abc");
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(response).thenReturn(response).thenReturn(null).thenReturn(response).thenReturn(json);
//        Mockito.when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(list1);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(map);
        Mockito.when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map1).thenReturn(map1).thenReturn(list1);
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        RolesSchema rolesSchema = new RolesSchema("1","name");
//        Mockito.when(mockObjectMapper.convertValue(anyMap(),ArgumentMatchers.eq(RolesSchema.class))).thenReturn(rolesSchema);
        GetUserGroupSchema getUserGroupSchema = new GetUserGroupSchema("1","name");
        Mockito.when(mockObjectMapper.convertValue(any(),ArgumentMatchers.eq(GetUserGroupSchema.class))).thenReturn(getUserGroupSchema);
        Mockito.when(mockObjectMapper.convertValue(any(),ArgumentMatchers.eq(GroupsSaveSchema.class))).thenReturn(groupsSaveSchema);
        UserGroupsSchema userGroupsSchema1 = new UserGroupsSchema("1",list);
        userManagementInKeyCloak.assignUserGroup(userGroupsSchema);
        verify(mockTokenUtils,times(2)).getTokenFromContext();
    }
    @Test
    void deleteGroupTest() throws Exception
    {
        String response = RESPONSE;
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        WebClient webClient= mock(WebClient.class);
        Mockito.when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), anyString())).thenReturn(response);
        userManagementInKeyCloak.deleteGroup("1");
        verify(mockTokenUtils,times(1)).getTokenFromContext();
    }

    @Test
    void changePassword() throws Exception
    {
        when(mockUserServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list1);
        Mockito.when(globalMessageSource.get(anyString())).thenReturn("abc");
        Mockito.when(mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(hashMap);
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequestForUser(any(WebClient.class),anyString(), anyString())).thenReturn(response).thenReturn(null);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(), anyString(),ArgumentMatchers.any())).thenReturn(response).thenReturn(response);
        Assertions.assertThrows(MailException.class,()->userManagementInKeyCloak.changePassword());
        verify(mockTokenUtils,times(2)).getTokenFromContext();
    }
    @Test
    void setPassword() throws Exception
    {
        Map<String, String> map1 = new HashMap<>();
        map1.put("userName", "nandini");
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequestForUser(any(WebClient.class),anyString(), anyString())).thenReturn(response);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(), anyString(),any())).thenReturn(response).thenReturn(null);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(hashMap);
        //when(this.mockObjectMapper.readValue(response, new TypeReference<>() {})).thenReturn(map1);
        Mockito.when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map1);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.setPassword("abc"));
        userManagementInKeyCloak.setPassword("abc");
    }
    @Test
    void getAllRoles() throws Exception
    {   List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map = new HashMap<>();
        map.put("clientId","camunda-identity-service");
        list.add(map);
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(), anyString(),any())).thenReturn(response).thenReturn(response);
        Mockito.when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(list);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.getAllRoles());
       // verify(mockTokenUtils,times(1)).getTokenFromContext();
//        userManagementInKeyCloak.getClientMap("");
//        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.getClientMap(""));
    }
//    @Test
//    void deleteUser() throws Exception
//    {
//        String response = RESPONSE;
//        List<Map<String, Object>> list1 = new ArrayList<>();
//        Map<String, Object> map1 = new HashMap<>();
//        map1.put("userName", "nandini");
//        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
//        list1.add(map1);
//        WebClient webClient= WebClient.builder().build();
//        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
//        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), ArgumentMatchers.eq(null))).thenReturn(response).thenReturn(null);
//        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
//        Mockito.when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map1);
//        userManagementInKeyCloak.deleteUser("name");
//        verify(mockTokenUtils,times(1)).getTokenFromContext();
//    }


    @Test
    void createUserTestTokenEmpty() {
        UserDataSchema userDataSchema = new UserDataSchema(map1,"userId");
        UserData userData = new UserData("id","jaga","ab","cd","1234567890","jaga@gmail.com","software");
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userData);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.createUser(userDataSchema));
    }

    @Test
    void createUserTestNullUserDetails() throws JsonProcessingException {
        HashMap<String,String> hashMap = new HashMap<>();
        hashMap.put("ab","value");
        hashMap.put("key","value");
        String response=RESPONSE;
        UserDataSchema userDataSchema1 = new UserDataSchema(map1,null);
        UserData userData = new UserData("id","jaga","ab","cd","1234567890","jaga@gmail.com","software");
        when(mockTokenUtils.getTokenFromContext()).thenReturn("token");
        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userData);
        WebClient webClient = WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), any())).thenReturn("");
        when(webClientWrapper.webclientRequestForUser(any(WebClient.class), anyString(), anyString())).thenReturn(response);
        when(this.mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(hashMap);
        Map<String, Object> response1 =userManagementInKeyCloak.createUser(userDataSchema1);
        Assertions.assertNotNull(response1);
        //Assertions.assertThrows(UserNotFoundException.class,()->userManagementInKeyCloak.createUser(userDataSchema1));
    }

        @Test
        void createUserTest() throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        map.put(AccountConstants.REALM_ROLES,"value");

        Map<String,Object> map1 = new HashMap<>();
        map1.put("key","value");
        map1.put("abc","value");

        HashMap<String,String> hashMap = new HashMap<>();
        hashMap.put("id","value");
        hashMap.put("username","value");

        UserDataSchema userDataSchema = new UserDataSchema(map1,"userId");
        UserDataSchema userDataSchema1 = new UserDataSchema(map1,null);
        WebClient webClient = WebClient.builder().build();
        UserData userData = new UserData("id","jaga","ab","cd","1234567890","jaga@gmail.com","software");
        when(mockTokenUtils.getTokenFromContext()).thenReturn("token").thenReturn("token").thenReturn("");
        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userData);
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        String response = RESPONSE;
        when(this.mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(hashMap);

        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), any())).thenReturn("abc");
        //when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), eq(null))).thenReturn(response);
        when(webClientWrapper.webclientRequestForUser(any(WebClient.class), anyString(), anyString())).thenReturn(response);
        when(this.mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.createUser(userDataSchema));
        //Map<String, Object> response1 = userManagementInKeyCloak.createUser(userDataSchema);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.createUser(userDataSchema1));
        // Assertions.assertThrows(RuntimeException.class,()->userManagementInKeyCloak.createUser(userDataSchema));
        //Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.createUser(userDataSchema));
        //Assertions.assertNotNull(response1);
        //Assertions.assertNotNull(response2);
    }
//    @Test
//    void createUserTestException() throws Exception
//    {
//        Map<String,Object> map1 = new HashMap<>();
//        map1.put("key","value");
//        map1.put("abc","value");
//        UserDataSchema userDataSchema = new UserDataSchema(map1,null);
//        UserData userData = new UserData(" ","jaga","ab","cd","1234567890","jaga@gmail.com","software");
//        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userData);
//        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
//        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), any())).thenReturn("abc");
//        userManagementInKeyCloak.createUser(userDataSchema);
//
//    }

//    @Test
//    void createUserExceptionTest() throws JsonProcessingException {
//        UserDataSchema userDataSchema = new UserDataSchema(map1,"userId");
//        UserData userData = new UserData("id","jaga","ab","cd","1234567890","jaga@gmail.com","software");
//        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userData);
//
//        Map<String,String> map = new HashMap<>();
//        map.put("key","value");
//        map.put("abc","value");
//        String response = "abc";
//        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), any())).thenReturn(response);
//        when( this.mockObjectMapper.readValue(response, new TypeReference<>() {})).thenReturn(map);
//        userManagementInKeyCloak.createUser(userDataSchema);
//    }

    @Test
    void deleteUserTest() throws JsonProcessingException {
        HashMap<String,String> hashMap = new HashMap<>();
        hashMap.put("id","value");
        hashMap.put("username","value");

        Map<String, String> map = new HashMap<>();
        map.put(AccountConstants.REALM_ROLES,"value");

        WebClient webClient = WebClient.builder().build();
        when(mockTokenUtils.getTokenFromContext()).thenReturn("token");
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        String response = RESPONSE;
        when(this.mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(hashMap);
        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), any())).thenReturn("abc");
        when(webClientWrapper.webclientRequestForUser(any(WebClient.class), anyString(), anyString())).thenReturn(response);
        when(this.mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.deleteUser("s"));
        verify(mockTokenUtils,times(1)).getTokenFromContext();
    }

    @Test
    void assignUserRoleTest() throws JsonProcessingException {

        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map2 = new HashMap<>();
        map2.put("clientId","camunda-identity-service");
        list.add(map2);

        Map<String, String> map3 = new HashMap<>();
        map3.put(AccountConstants.REALM_ROLES,"value");

        HashMap<String, Object> map = new HashMap<>();
        map.put(AccountConstants.REALM_ROLES,List.of("value"));

        String roles = "roles";
        RolesSchema rolesSchema = new RolesSchema("roleID","roleName");
        UserRolesSchema userRolesSchema =  new UserRolesSchema("userId",List.of(roles));

        HashMap<String,String> hashMap = new HashMap<>();
        hashMap.put("id","value");
        hashMap.put("username","value");

        String response = RESPONSE;
        when(mockTokenUtils.getTokenFromContext()).thenReturn("token");
        WebClient webClient = WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        when(this.mockObjectMapper.convertValue(any(),eq(HashMap.class))).thenReturn(hashMap);
        when(this.mockObjectMapper.convertValue(any(),eq(RolesSchema.class))).thenReturn(rolesSchema);
        Mockito.when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map).thenReturn(list).thenReturn(list).thenReturn(list).thenReturn(list).thenReturn(map3);
        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(),anyString(), any())).thenReturn(response).thenReturn(response).thenReturn(null).thenReturn(response);
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.assignUserRole(userRolesSchema));
        verify(mockTokenUtils,times(6)).getTokenFromContext();
    }

    @Test
    void getClientMapTestTokenEmpty(){
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.getClientMap(""));
    }

    @Test
    void assignUserRoleTestTokenEmpty(){
        String roles = "roles";
        UserRolesSchema userRolesSchema =  new UserRolesSchema("userId",List.of(roles));
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.assignUserRole(userRolesSchema));
    }

    @Test
    void deleteUserTestTokenEmpty(){
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.deleteUser("s"));
    }

    @Test
    void getAllGroupsTestTokenEmpty(){
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.getAllGroups());
    }

    @Test
    void getGroupByIdTestTokenEmpty(){
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.getGroupById("Id"));
    }

    @Test
    void createGroupTestTokenEmpty(){
        GroupsSchema groupsSchema = new GroupsSchema("1","abc");
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.createGroup(groupsSchema));
    }

    @Test
    void deleteGroupTestTokenEmpty(){
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.deleteGroup("Id"));
    }

    @Test
    void assignUserGroupTestTokenEmpty(){
        List<String> list = new ArrayList<>();
        list.add("abc");
        UserGroupsSchema userGroupsSchema = new UserGroupsSchema("abc",list);
        when(mockTokenUtils.getTokenFromContext()).thenReturn("");
        Assertions.assertThrows(InvalidInputException.class,()->userManagementInKeyCloak.assignUserGroup(userGroupsSchema));
    }
    @Test
    void addRolesSucess() throws JsonProcessingException {
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map2 = new HashMap<>();
        map2.put("id","98958-492");
        map2.put("clientId","camunda-identity-service");
        list.add(map2);
        RolesDto rolesDto = new RolesDto();
        rolesDto.setDescription("abc");
        rolesDto.setName("abc");
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        Mockito.when(mockObjectMapper.readValue(anyString(),eq(List.class))).thenReturn(list);
        WebClient webClient = WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(), anyString(),any())).thenReturn(response);
        userManagementInKeyCloak.addRoles("camunda-identity-service",rolesDto);
        Mockito.verify(webClientWrapper,times(1)).createWebClient(any());
    }
    @Test
    void addRolesExceptionForClientsNotFound() throws JsonProcessingException {
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map2 = new HashMap<>();
        map2.put("id","98958-492");
        map2.put("clientId","camunda-identity-service");
        list.add(map2);
        RolesDto rolesDto = new RolesDto();
        rolesDto.setDescription("abc");
        rolesDto.setName("abc");
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        Mockito.when(mockObjectMapper.readValue(anyString(),eq(List.class))).thenThrow(JsonProcessingException.class);
        WebClient webClient = WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(), anyString(),any())).thenReturn(null);
        Assertions.assertThrows(RunTimeException.class,()->userManagementInKeyCloak.addRoles("camunda-identity-service",rolesDto));
    }
    @Test
    void addRolesExceptionForClientNotFound() throws JsonProcessingException {
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map2 = new HashMap<>();
        map2.put("id","98958-492");
        map2.put("clientId","camunda-service");
        list.add(map2);
        RolesDto rolesDto = new RolesDto();
        rolesDto.setDescription("abc");
        rolesDto.setName("abc");
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        Mockito.when(mockObjectMapper.readValue(anyString(),eq(List.class))).thenReturn(list);
        WebClient webClient = WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(), anyString(),any())).thenReturn(response);
        Assertions.assertThrows(RunTimeException.class,()->userManagementInKeyCloak.addRoles("camunda-identity-service",rolesDto));
    }
}
