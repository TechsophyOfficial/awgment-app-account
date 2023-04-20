package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.InternalControllerImpl;
import com.techsophy.tsf.account.dto.UserData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.impl.UserFormDataServiceImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
class InternalControllerImplTest {
    @Mock
    UserFormDataDefinitionRepository userFormDataRepository;
    @Mock
    UserServiceImpl userServiceImpl;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl idGenerator;
    @Mock
    TokenUtils tokenUtils;
    @Mock
    UserDetails userDetails;
    @Mock
    UserDefinitionRepository userDefinitionRepository;
    @Mock
    Logger log;
    @InjectMocks
    InternalControllerImpl internalUserFormDataServiceImpl;
    @Mock
    UserFormDataServiceImpl userFormDataService;
    Map<String,Object> userDataMap = new HashMap<>();


    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveUserFormData() throws Exception{

        userDataMap.put(USERNAME_INTERNAL,USERNAME_INTERNAL_VALUE);
        userDataMap.put(FIRSTNAME_INTERNAL,FIRSTNAME_INTERNAL_VALUE);
        userDataMap.put(LASTNAME_INTERNAL,LASTNAME_INTERNAL_VALUE);
        userDataMap.put(MOBILE_NUMBER_INTERNAL,MOBILE_NUMBER_INTERNAL_VALUE);
        userDataMap.put(DEPARTMENT_INTERNAL,DEPARTMENT_INTERNAL_VALUE);
        userDataMap.put(SIGNATURE_INTERNAL,SIGNATURE_INTERNAL_VALUE);
        userDataMap.put("realmId","master");

        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition();
        userFormDataDefinition1.setUserData(userDataMap);
        userFormDataDefinition1.setUserId(BigInteger.TWO);
        userFormDataDefinition1.setVersion(1);

        UserData userData = new UserData();
        userData.setUserName(USERNAME_INTERNAL_VALUE);
        userData.setFirstName(FIRSTNAME_INTERNAL_VALUE);
        userData.setLastName(LASTNAME_INTERNAL_VALUE);
        userData.setMobileNumber(MOBILE_NUMBER_INTERNAL_VALUE);
        userData.setEmailId(USERNAME_INTERNAL_VALUE);
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
        HttpHeaders headers = null;
        Assertions.assertNotNull(internalUserFormDataServiceImpl.saveUser("signature", userFormDataSchema,headers));
    }

}