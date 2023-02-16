package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.AccountConstants;
import com.techsophy.tsf.account.constants.UserPreferencesConstants;
import com.techsophy.tsf.account.dto.ACLSchema;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

}
