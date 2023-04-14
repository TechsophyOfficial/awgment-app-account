package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.AccountConstants;
import com.techsophy.tsf.account.constants.UserPreferencesConstants;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.CheckACLSchema;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.EntityNotFoundByIdException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.repository.ACLRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.impl.ACLServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.*;

import static com.techsophy.tsf.account.constants.ACLConstants.TEST_TOKEN;
import static com.techsophy.tsf.account.constants.AccountConstants.CREATED_ON;
import static com.techsophy.tsf.account.constants.AccountConstants.EMAIL_ID;
import static com.techsophy.tsf.account.constants.AccountConstants.LAST_NAME;
import static com.techsophy.tsf.account.constants.AccountConstants.UPDATED_BY_ID;
import static com.techsophy.tsf.account.constants.AccountConstants.UPDATED_BY_NAME;
import static com.techsophy.tsf.account.constants.AccountConstants.UPDATED_ON;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ThemesConstants.*;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.CREATED_BY_ID;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.DEPARTMENT;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.FIRST_NAME;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.MOBILE_NUMBER;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.NULL;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.NUMBER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ACLServiceExceptionTest
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
    @Mock
    UserFormDataService userFormDataService;
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
        map.put(AccountConstants.ID,UserPreferencesConstants.EMPTY_STRING);
        map.put(USER_NAME_DATA, USER_FIRST_NAME);
        map.put(FIRST_NAME, USER_LAST_NAME);
        map.put(LAST_NAME, USER_FIRST_NAME);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);
    }

    @Test
    void saveACLUserDetailsNotFoundExceptionTest() throws JsonProcessingException
    {
        Mockito.when(mockUserDetails.getUserDetails())
                .thenReturn(userList);
        ACLSchema aclSchema=new ACLSchema();
        Assertions.assertThrows(UserDetailsIdNotFoundException.class,()->aclService.saveACL(aclSchema));
    }

    @Test
    void getACLByIdEntityNotFoundExceptionTest()
    {
        Assertions.assertThrows(EntityNotFoundByIdException.class,()->aclService.getACLById(ID_VALUE));
    }

    @Test
    void checkACLAccessEntityNotFoundExceptionTest()
    {
        Map<String,Object> userData=new HashMap<>();
        userData.put("emailId","nandini.k@techsophy.com");
        userData.put("mobileNumber","9381837179");
        userData.put("firstName","Ganga");
        userData.put("lastName","nandini");
        userData.put("userName","nandini");
        AuditableData auditableData = new AuditableData();
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userData,"12","1");
        Mockito.when(userFormDataService.getUserFormDataByUserId(any(),any())).thenReturn(auditableData);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(UserFormDataSchema.class))).thenReturn(userFormDataSchema);
        Mockito.when(mockUserDetails.getUserId()).thenReturn(Optional.of(BigInteger.valueOf(1)));
        CheckACLSchema checkACLSchema=new CheckACLSchema();
        Mockito.when(mockTokenUtils.getTokenFromContext()).thenReturn(TEST_TOKEN);
        Assertions.assertThrows(EntityNotFoundByIdException.class,()->aclService.checkACLAccess(ID_VALUE,checkACLSchema));
    }
}
