package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.assertions.Assertions;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.ACLEntry;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.repository.ACLRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.impl.ACLServiceImpl;
import com.techsophy.tsf.account.service.impl.Rules;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigInteger;
import java.nio.file.AccessDeniedException;
import java.util.*;

import static com.techsophy.tsf.account.constants.ACLConstants.TEST_TOKEN;
import static com.techsophy.tsf.account.constants.ACLConstants.*;
import static com.techsophy.tsf.account.constants.AccountConstants.CREATED_BY_ID;
import static com.techsophy.tsf.account.constants.AccountConstants.CREATED_ON;
import static com.techsophy.tsf.account.constants.AccountConstants.EMAIL_ID;
import static com.techsophy.tsf.account.constants.AccountConstants.FIRST_NAME;
import static com.techsophy.tsf.account.constants.AccountConstants.ID;
import static com.techsophy.tsf.account.constants.AccountConstants.LAST_NAME;
import static com.techsophy.tsf.account.constants.AccountConstants.UPDATED_BY_ID;
import static com.techsophy.tsf.account.constants.AccountConstants.UPDATED_BY_NAME;
import static com.techsophy.tsf.account.constants.AccountConstants.UPDATED_ON;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ThemesConstants.*;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.DEPARTMENT;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.MOBILE_NUMBER;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.NULL;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.NUMBER;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ACLServiceTest
{
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl mockIdGenerator;
    @Mock
    TokenUtils mockTokenUtils;
    @Mock
    UserFormDataDefinitionRepository userFormDataDefinitionRepository;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    UserDetails mockUserDetails;
    @Mock
    ACLRepository aclRepository;
    @InjectMocks
    ACLServiceImpl aclService;

    List<Map<String, Object>> userList = new ArrayList<>();

    @BeforeEach
    public void init()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(CREATED_BY_ID, NULL);
        map.put(CREATED_BY_NAME, NULL);
        map.put(CREATED_ON, NULL);
        map.put(UPDATED_BY_ID, NULL);
        map.put(UPDATED_BY_NAME, NULL);
        map.put(UPDATED_ON, NULL);
        map.put(ID, BIGINTEGER_ID);
        map.put(USER_NAME_DATA, USER_FIRST_NAME);
        map.put(FIRST_NAME, USER_LAST_NAME);
        map.put(LAST_NAME, USER_FIRST_NAME);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);
    }

    @Test
    void saveACLFreshRecordTest() throws JsonProcessingException
    {
        Mockito.when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        ACLSchema aclSchema=new ACLSchema();
        when(mockIdGenerator.nextId()).thenReturn(BigInteger.valueOf(Long.parseLong(BIGINTEGER_ID)));
        ACLDefinition aclDefinition=new ACLDefinition();
        Mockito.when(mockObjectMapper.convertValue(any(), ArgumentMatchers.eq(ACLDefinition.class))).thenReturn(aclDefinition);
        Assertions.assertNotNull(aclService.saveACL(aclSchema));
    }

    @Test
    void updateACLRecordTest() throws JsonProcessingException
    {
        Mockito.when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        ACLSchema aclSchema=new ACLSchema();
        aclSchema.setId(ID_VALUE);
        ACLDefinition aclDefinition=new ACLDefinition();
        Mockito.when(mockObjectMapper.convertValue(any(), ArgumentMatchers.eq(ACLDefinition.class))).thenReturn(aclDefinition);
        Mockito.when(aclRepository.updateACLDefinition(any(),any())).thenReturn(aclSchema);
        Assertions.assertNotNull(aclService.saveACL(aclSchema));
    }

    @Test
    void getAllACLsTest()
    {
        List<ACLDefinition> aclDefinitionList=new ArrayList<>();
        ACLDefinition aclDefinition=new ACLDefinition();
        aclDefinitionList.add(aclDefinition);
        Pageable pageable=PageRequest.of(1,5);
        PaginationResponsePayload paginationResponsePayload=new PaginationResponsePayload();
        paginationResponsePayload.setContent(new ArrayList<>());
        paginationResponsePayload.setPage(0);
        paginationResponsePayload.setSize(5);
        paginationResponsePayload.setTotalPages(10);
        paginationResponsePayload.setNumberOfElements(100);
        Mockito.when(aclRepository.findAll(pageable)).thenReturn(new PageImpl<>(aclDefinitionList,pageable,100));
        Mockito.when(mockTokenUtils.getPaginationResponsePayload(any(),any())).thenReturn(paginationResponsePayload);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(Map.class))).thenReturn(new HashMap());
        Assertions.assertNotNull(aclService.getAllACLs(PageRequest.of(1,5)));
    }

    @Test
    void getAclByIdTest()
    {
        ACLDefinition aclDefinition=new ACLDefinition();
        ACLSchema aclSchema=new ACLSchema();
        Mockito.when(mockObjectMapper.convertValue(aclDefinition,ACLSchema.class)).thenReturn(aclSchema);
        Mockito.when(aclRepository.findById(any())).thenReturn(Optional.of(aclDefinition));
        Assertions.assertNotNull(aclService.getACLById(ID_VALUE));
    }

    @Test
    void checkAclAccessTest() throws JsonProcessingException, AccessDeniedException
    {
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
        Map<String,Object> response=new HashMap<>();
        response.put(PREFERED_USERNAME,"akhil");
        UserFormDataDefinition userFormDataDefinition=new UserFormDataDefinition();
        userFormDataDefinition.setId(BigInteger.valueOf(Long.parseLong(ID_VALUE)));
        Map<String,Object> userData=new HashMap<>();
        List<String> groupsList=new ArrayList<>();
        groupsList.add("devops");
        userData.put(GROUPS,groupsList);
        userData.put(PREFERED_USERNAME,"akhil");
        userFormDataDefinition.setUserData(userData);
        ACLDefinition aclDefinition=new ACLDefinition();
        aclDefinition.setName(ACL_NAME);
        ACLEntry aclEntry =new ACLEntry();
        aclEntry.setDecision(ALLOW);
        aclEntry.setRuleType(Rules.USERS);
        List<String> usersList=new ArrayList<>();
        usersList.add(USER_NAME_VALUE);
        aclEntry.setData(usersList);
        aclEntry.setAdditionalDetails(new HashMap<>());
        List<ACLEntry> aclEntryList =new ArrayList<>();
        aclEntryList.add(aclEntry);
        ACLEntry aclEntry1=new ACLEntry();
        aclEntry1.setDecision(DENY);
        aclEntry1.setRuleType(Rules.GROUPS);
        aclEntry1.setData(groupsList);
        aclEntryList.add(aclEntry1);
        ACLEntry aclEntry2=new ACLEntry();
        aclEntry2.setDecision(DENY);
        aclEntry2.setRuleType(Rules.GROUPS);
        aclEntry2.setData(groupsList);
        aclEntryList.add(aclEntry2);
        ACLEntry aclEntry3=new ACLEntry();
        aclEntry3.setDecision(ALLOW);
        aclEntry3.setRuleType(Rules.SYSTEM);
        aclEntry3.setData(SYSTEM);
        aclEntryList.add(aclEntry3);
        ACLEntry aclEntry4=new ACLEntry();
        aclEntry4.setDecision(ALLOW);
        aclEntry4.setRuleType(Rules.ALL);
        aclEntry4.setData("ALL");
        ACLEntry aclEntry5=new ACLEntry();
        aclEntry5.setDecision(ALLOW);
        aclEntry5.setRuleType(Rules.ALL);
        List<String> rolesList=new ArrayList<>();
        rolesList.add("akhilTestingRole");
        aclEntry5.setData(rolesList);
        userData.put("roles",rolesList);
        aclDefinition.setRead(aclEntryList);
        aclDefinition.setUpdate(aclEntryList);
        aclDefinition.setDelete(aclEntryList);
        Mockito.when(aclRepository.findById(any())).thenReturn(Optional.of(aclDefinition));
        Mockito.when(mockTokenUtils.getUserInformationMap(anyString())).thenReturn(userData);
        Assertions.assertNotNull(aclService.checkACLAccess(ID_VALUE));
    }

    @Test
    void aclNullValidateTest() throws AccessDeniedException, JsonProcessingException
    {
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
        Map<String,Object> userData=new HashMap<>();
        ACLDefinition aclDefinition=new ACLDefinition();
        aclDefinition.setName(ACL_NAME);
        List<ACLEntry> aclEntryList =new ArrayList<>();
        aclDefinition.setRead(aclEntryList);
        aclDefinition.setUpdate(aclEntryList);
        aclDefinition.setDelete(aclEntryList);
        Mockito.when(aclRepository.findById(any())).thenReturn(Optional.of(aclDefinition));
        Mockito.when(mockTokenUtils.getUserInformationMap(anyString())).thenReturn(userData);
        Assertions.assertNotNull(aclService.checkACLAccess(ID_VALUE));
    }
}
