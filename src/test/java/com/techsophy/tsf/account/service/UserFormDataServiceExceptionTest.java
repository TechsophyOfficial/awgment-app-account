package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.exception.UserNameValidationException;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.SERVICE_ACCOUNT;
import static com.techsophy.tsf.account.constants.UserConstants.USER_STRING;

@ExtendWith(MockitoExtension.class)
class UserFormDataServiceExceptionTest
{
    @Mock
    UserFormDataDefinitionRepository mockUserFormDataDefinitionRepository;
    @Mock
    UserServiceImpl mockUserServiceImpl;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl mockIdGenerator;
    @Mock
    TokenUtils accountUtils;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    TokenUtils mockUtilityService;
    @InjectMocks
    UserDetails userDetails;
    @Mock
    AuditableData auditableData;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    UserDetails mockUserDetails;
    UserDefinition userDefinition=new UserDefinition();

    List<Map<String,Object>> list = new ArrayList<>();
    Map<String,Object> map = new HashMap<>();
    @BeforeEach
    public void init()
    {
        list = new ArrayList<>();map = new HashMap<>();
        map.put("abc","abc");
        map.put("id",1);
        map.put("userId",1);
        list.add(map);
        userDefinition.setFirstName(USER_STRING);
        userDefinition.setCreatedById(BigInteger.valueOf(234234234));
        userDefinition.setId(BigInteger.valueOf(345345));
    }

    @Test
    void userNameValidationExceptionTest()
    {
        Assertions.assertThrows(UserNameValidationException.class,()->userDetails.userNameValidations(SERVICE_ACCOUNT));
    }
}
