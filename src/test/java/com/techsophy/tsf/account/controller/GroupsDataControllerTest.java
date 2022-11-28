package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.controller.impl.GroupsDataControllerImpl;
import com.techsophy.tsf.account.service.GroupsDataService;
import com.techsophy.tsf.account.dto.AssignGroupRoles;
import com.techsophy.tsf.account.dto.GroupsData;
import com.techsophy.tsf.account.dto.GroupsDataSchema;
import com.techsophy.tsf.account.utils.TokenUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.Instant;
import java.util.*;
import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;
import static com.techsophy.tsf.account.constants.UserConstants.ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

//@SpringBootTest
@ActiveProfiles(TEST_ACTIVE_PROFILE)
@ExtendWith({MockitoExtension.class, SpringExtension.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupsDataControllerTest
{
    @Mock
    GroupsDataService groupsDataService;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    private HttpHeaders headers;
    @Mock
    TokenUtils accountUtils;
    @InjectMocks
    GroupsDataControllerImpl groupsDataControllerImpl;

    MockHttpServletRequest request = new MockHttpServletRequest();
    String token;
    List<String> roles = new ArrayList<>();

    @BeforeEach
    public void init()
    {
        roles.add("e8d5cea9-1215-45e9-b54a-0f7a2d6e0880");
    }
    GroupsData groupsData = new GroupsData("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880");
    GroupsData groupsData1 = new GroupsData(null, "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880");
    GroupsDataSchema groupsDataSchema = new GroupsDataSchema("123", "test", "description", "e8d5cea9-1215-45e9-b54a-0f7a2d6e0880",
           roles ,"123", Instant.now(),"123",Instant.now());

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void saveGroupTest()
    {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-USERID", "X-Test-Value");
        Mockito.when(groupsDataService.saveGroup(groupsData)).thenReturn(groupsDataSchema);
        ApiResponse responseEntity = groupsDataControllerImpl.saveGroup(groupsData);
        ApiResponse responseEntity1 = groupsDataControllerImpl.saveGroup(groupsData1);
        GroupsDataSchema groupsDataSchema = groupsDataService.saveGroup(groupsData);
        assertEquals(true, responseEntity.getSuccess());
        assertEquals(true, responseEntity1.getSuccess());
        verify(groupsDataService,times(1)).saveGroup(groupsData);
    }

    @Test
    void getGroupByIdTest() throws JsonProcessingException
    {
        when(groupsDataService.getGroupById(ID)).thenReturn(groupsDataSchema);
        ApiResponse responseEntity = groupsDataControllerImpl.getGroupById(ID);
        assertEquals(true, responseEntity.getSuccess());
        GroupsDataSchema groupsDataSchema= groupsDataService.getGroupById(ID);
        verify(groupsDataService,times(1)).getGroupById(ID);
    }

    @Test
    void deleteGroupTest() throws JsonProcessingException
    {
        ApiResponse<Void> responseEntity=groupsDataControllerImpl.deleteGroup(ID);
        assertEquals(true,responseEntity.getSuccess());
        groupsDataService.deleteGroup(ID);
    }

    @Test
    void assignRolesToGroupTest() throws JsonProcessingException
    {
        groupsDataService.assignRolesToGroup(ID,new AssignGroupRoles(List.of("e8d5cea9-1215-45e9-b54a-0f7a2d6e0880")));
        ApiResponse responseEntity=groupsDataControllerImpl.assignRolesToGroup(ID,new AssignGroupRoles(List.of("e8d5cea9-1215-45e9-b54a-0f7a2d6e0880")));
        assertEquals(true,responseEntity.getSuccess());
    }
    @Test
    void getAllGroups() throws Exception
    {
        PageRequest page = PageRequest.of(1,1);
        ApiResponse response = groupsDataControllerImpl.getAllGroups("q",1,1,null,"abc");
        groupsDataControllerImpl.getAllGroups("q",null,1,null,"abc");
        Assertions.assertNotNull(response);
    }
}
