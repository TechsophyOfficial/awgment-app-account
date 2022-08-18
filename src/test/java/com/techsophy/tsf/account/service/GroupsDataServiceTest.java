package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.AccountConstants;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.GroupDefinition;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.repository.GroupRepository;
import com.techsophy.tsf.account.service.impl.GroupsDataServiceImpl;
import com.techsophy.tsf.account.service.impl.UserManagementInKeyCloakImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.Cleanup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static com.techsophy.tsf.account.constants.GroupsDataServiceConstants.RESPONSE;
import static com.techsophy.tsf.account.constants.GroupsDataServiceConstants.USER_DEFINITION;
import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import com.techsophy.tsf.account.exception.GroupsNotFoundException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
//@ActiveProfiles(TEST_ACTIVE_PROFILE)
//@SpringBootTest
class GroupsDataServiceTest {
    @Mock
    UserManagementInKeyCloakImpl userManagementInKeyCloak;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    GroupRepository groupRepository;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    TokenUtils mockTokenUtils;
    @InjectMocks
    GroupsDataServiceImpl groupsDataServiceImpl;
    @Mock
    UserServiceImpl userServiceImpl;
    @Mock
    IdGeneratorImpl idGenerator;
    List<String> roles = new ArrayList<>();

    @Test
    void getAllGroups() throws Exception {
        Long l = 1L;
        // GroupsDataSchema groupsDataSchema = new GroupsDataSchema("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880",
        //    roles ,"123", Instant.now(),"createdByName","123",Instant.now(),"updatedByName");
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "abc");
        Mockito.when(groupRepository.findGroupsByQSorting("abc", null)).thenReturn(List.of(groupDefinition));
        groupsDataServiceImpl.getAllGroups("abc", null, null);
        verify(groupRepository, times(1)).findGroupsByQSorting("abc", null);
    }

    @Test
    void getAllGroupsEx() throws Exception {
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "abc");
        Mockito.when(groupRepository.findByIdIn(any())).thenReturn(List.of(groupDefinition));
        List<GroupsDataSchema> response = groupsDataServiceImpl.getAllGroups("abc", null, "abc");
        Assertions.assertNull(response);
    }

    @Test
    void getAllGroupsQnullTest() throws Exception {
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "abc");
        Mockito.when(groupRepository.findAll((Sort) any())).thenReturn(List.of(groupDefinition));
        List<GroupsDataSchema> response = groupsDataServiceImpl.getAllGroups("", null, "");
        Assertions.assertNull(response);
    }

    @Test
    void getAllGroupsTest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "abc");
        Page<GroupDefinition> tasks = Mockito.mock(Page.class);
        Mockito.when(this.groupRepository.findGroupsByQPageable("abc", null)).thenReturn(tasks);
        groupsDataServiceImpl.getAllGroups("abc", null);
        verify(groupRepository, times(1)).findGroupsByQPageable("abc", null);
    }
    @Test
    void getAllGroupsNullTest() throws Exception {
        Page<GroupDefinition> tasks = Mockito.mock(Page.class);
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "abc");
        when(groupRepository.findAll((Pageable) any())).thenReturn(tasks);
        groupsDataServiceImpl.getAllGroups("", null);
        verify(groupRepository, times(1)).findAll((Pageable) null);
    }

    @Test
    void deleteGroups() {
        when(groupRepository.existsById(BigInteger.valueOf(123))).thenReturn(true);
        doNothing().when(groupRepository).deleteById(BigInteger.valueOf(Long.parseLong("123")));
        groupsDataServiceImpl.deleteGroup("123");
        verify(groupRepository, times(1)).deleteById(BigInteger.valueOf(Long.parseLong("123")));
    }

    @Test
    void saveGroups() throws Exception {
        InputStream inputStreamTest = new ClassPathResource(USER_DEFINITION).getInputStream();
        ObjectMapper objectMapperTest = new ObjectMapper();
        UserDefinition userDefintion = objectMapperTest.readValue(inputStreamTest, UserDefinition.class);
        Map<String, Object> map = new HashMap<>();
        map.put("id", "1");
        when(userServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(List.of(map));
        GroupsData groupsData1 = new GroupsData(null, "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880");
        GroupsDataSchema groupsDataSchema = new GroupsDataSchema("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880",
                roles, "123", Instant.now(), "createdByName", "123", Instant.now(), "updatedByName");
        GroupsData groupsData = new GroupsData("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880");
        when(mockObjectMapper.convertValue(any(), eq(GroupsDataSchema.class))).thenReturn(groupsDataSchema);
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "1");
        when(mockObjectMapper.convertValue(any(), eq(GroupDefinition.class))).thenReturn(groupDefinition);
        when(groupRepository.findAll()).thenReturn(List.of(groupDefinition));
        when(groupRepository.existsById(BigInteger.valueOf(123))).thenReturn(true);
        when(groupRepository.existsByName(groupsData.getName())).thenReturn(false);
        when(groupRepository.findById(BigInteger.valueOf(123))).thenReturn(Optional.of(groupDefinition));
        when(groupRepository.save(groupDefinition)).thenReturn(groupDefinition.withId(BigInteger.valueOf(Long.parseLong("1"))));
        groupsDataServiceImpl.saveGroup(groupsData);
        groupsDataServiceImpl.saveGroup(groupsData1);
        verify(groupRepository, times(1)).existsById(BigInteger.valueOf(123));
    }

    @Test
    void groupsPagination() {
        GroupsDataSchema groupsDataSchema = new GroupsDataSchema("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880",
                roles, "123", Instant.now(), "createdByName", "123", Instant.now(), "updatedByName");
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(1), "abc", "abc", "1");
        List<Map<String, Object>> list = new ArrayList<>();
        Page<GroupDefinition> groupDefinitions = new PageImpl<>(List.of(groupDefinition));
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "bar");
        list.add(map);
        when(mockObjectMapper.convertValue(any(),eq(GroupsDataSchema.class))).thenReturn(groupsDataSchema);
        when(mockObjectMapper.convertValue(any(), eq(Map.class))).thenReturn(map);
        when(mockTokenUtils.getPaginationResponsePayload(groupDefinitions, list)).thenReturn(new PaginationResponsePayload());
        groupsDataServiceImpl.groupsPagination(groupDefinitions);
        verify(mockTokenUtils, times(1)).getPaginationResponsePayload(groupDefinitions, list);
    }

//    @Test
//    void getGroupsById() throws Exception
//    {
//      ObjectMapper mapper = new ObjectMapper();
//        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
//        Map<String, Object> map = new HashMap<String, Object>();
//        map.put("foo", "bar");
//        map.put(AccountConstants.REALM_ROLES,List.of("abc"));
//        list.add(map);
//        String json = mapper.writeValueAsString(list);
//        String response1=RESPONSE;
//        String response=json;
//        WebClient webClient= WebClient.builder().build();
//        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
//        when(mockObjectMapper.readValue(anyString(),any(TypeReference.class))).thenReturn(map);
//        GroupDefinition groupDefinition = new GroupDefinition( BigInteger.valueOf(1),"abc","abc","1");
//        when(groupRepository.findById(BigInteger.valueOf(1))).thenReturn(Optional.of(groupDefinition));
//        GroupsDataSchema groupsDataSchema = new GroupsDataSchema("1", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880",
//                roles ,"123", Instant.now(),"createdByName","123",Instant.now(),"updatedByName");
//        when(mockObjectMapper.convertValue(any(),eq(GroupsDataSchema.class))).thenReturn(groupsDataSchema);
//        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(),eq(null))).thenReturn(response1).thenReturn(response).thenReturn(null);
//       Mockito.when(mockObjectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(map).thenReturn(List.of(new HashMap<String,Object>()));
//      //  Mockito.when(mockObjectMapper.readValue(anyString(),any(Object.class())).thenReturn(values);
//        groupsDataServiceImpl.getGroupById("1");
//        Assertions.assertThrows(GroupsNotFoundException.class,()->groupsDataServiceImpl.getGroupById("1"));
//    }

//    @Test
//    void assignRolesToGroupTest() throws Exception
//    {
//        GroupDefinition groupDefinition = new GroupDefinition( BigInteger.valueOf(1),"abc","abc","1");
//        List<String> list = new ArrayList<>();
//        list.add("role");
//        List<Map<String, Object>> list1 = new ArrayList<Map<String, Object>>();
//        Map<String, Object> map1 = new HashMap<String, Object>();
//        map1.put("name", "role");
//        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
//        list1.add(map1);
//        AssignGroupRoles assignGroupRoles = new AssignGroupRoles(list);
//        String string="abc";
//        WebClient webClient= WebClient.builder().build();
//        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
//        when(groupRepository.existsById(BigInteger.valueOf(1))).thenReturn(true);
//        when(groupRepository.findById(BigInteger.valueOf(1))).thenReturn(Optional.of(groupDefinition));
//        String response1=RESPONSE;
//        Map<String, Object> map = new HashMap<String, Object>();
//        map.put("foo", "bar");
//        map.put(AccountConstants.REALM_ROLES,List.of("abc"));
//        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(),eq(null))).thenReturn(response1).thenReturn(response1);
//        Mockito.when(mockObjectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(list1);
//        groupsDataServiceImpl.assignRolesToGroup("1",assignGroupRoles);
//        verify(groupRepository,times(1)).existsById(BigInteger.valueOf(1));
//    }

    @Test
    void getGroupByIdTest() throws JsonProcessingException {
        String response = RESPONSE;

        List<String> list = new ArrayList<>();
        list.add("admin");
        list.add("user");

        List<Map<String, Object>> list1 = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("name", "role");
        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list1.add(map1);


        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("abc");
        arrayList.add("bcd");
        LinkedHashMap linkedHashMap = new LinkedHashMap<>();
        linkedHashMap.put(arrayList.get(0),"2");
        linkedHashMap.put(arrayList.get(1),"4");

        Map<String, Object> map = new HashMap<>();
        //map.put("foo", "bar");
        map.put(AccountConstants.REALM_ROLES,List.of("abc"));
        map.put(AccountConstants.CLIENT_ROLES,linkedHashMap);

        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(10), "abc", "abc", "1");
        GroupsDataSchema groupsDataSchema = new GroupsDataSchema("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880",
                roles, "123", Instant.now(), "createdByName", "123", Instant.now(), "updatedByName");
        WebClient webClient = WebClient.builder().build();
        when(mockTokenUtils.getTokenFromContext()).thenReturn(String.valueOf(webClient));
        when( webClientWrapper.createWebClient(any())).thenReturn(webClient);
        when(groupRepository.findById(BigInteger.valueOf(Long.parseLong("10")))).thenReturn(Optional.of(groupDefinition));
        when(this.mockObjectMapper.convertValue(any(), eq(GroupsDataSchema.class))).thenReturn(groupsDataSchema);
        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), eq(null))).thenReturn(response);
        when(mockObjectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(map).thenReturn(list1);
        when(this.mockObjectMapper.convertValue(any(),eq(List.class))).thenReturn(list);
        groupsDataServiceImpl.getGroupById("10");
        verify(groupRepository, times(1)).findById(any());
    }

    @Test
    void assignRolesToGroupTest() throws JsonProcessingException {
        List<Map<String, Object>> list1 = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("name", "role");
        map1.put(AccountConstants.REALM_ROLES,List.of("abc"));
        list1.add(map1);

        List<String> roles = new ArrayList<>();
        roles.add("abc");
        roles.add("b");

        Map<String,String> map = new HashMap<>();
        map.put("key","value");
        map.put("abc","value");

        RolesSchema rolesSchema = new RolesSchema("roleId","roleName");
        RolesSchema rolesSchema1 = new RolesSchema("roleId1","roleName1");
        List<RolesSchema> list = new ArrayList<>();
        list.add(rolesSchema);
        list.add(rolesSchema1);
        Map<String,List<RolesSchema>> map2 = new HashMap<>();
        map2.put("abc",list);

        AssignGroupRoles assignGroupRoles = new AssignGroupRoles(roles);
        GroupDefinition groupDefinition = new GroupDefinition(BigInteger.valueOf(10), "abc", "abc", "1");
        WebClient webClient = WebClient.builder().build();
        when(mockTokenUtils.getTokenFromContext()).thenReturn("token");
        when(mockTokenUtils.getTokenFromContext()).thenReturn(String.valueOf(webClient));
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        when(groupRepository.existsById(BigInteger.valueOf(Long.parseLong("10")))).thenReturn(true);
        when(groupRepository.findById(BigInteger.valueOf(Long.parseLong("10")))).thenReturn(Optional.of(groupDefinition));
        when(webClientWrapper.webclientRequest(any(WebClient.class), anyString(), anyString(), eq(null))).thenReturn(RESPONSE);
//        when(mockObjectMapper.readValue(anyString(),eq(new TypeReference<>(){}))).thenReturn(list1);
        when(userManagementInKeyCloak.getClientMap(any())).thenReturn(map);
        when(userManagementInKeyCloak.getAllClientAndDefaultRoles()).thenReturn(map2);
        groupsDataServiceImpl.assignRolesToGroup("10",assignGroupRoles);
        verify(groupRepository, times(1)).findById(any());
    }
}
