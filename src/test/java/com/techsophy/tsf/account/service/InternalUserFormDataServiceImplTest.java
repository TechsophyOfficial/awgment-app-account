package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.InternalUserFormDataSchema;
import com.techsophy.tsf.account.dto.UserData;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.impl.InternalServiceImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

class InternalUserFormDataServiceImplTest {
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
    UserPreferencesThemeService userPreferencesThemeService;
    @Mock
    Logger log;
    @InjectMocks
    InternalServiceImpl internalUserFormDataServiceImpl;
    Map<String,Object> userDataMap = new HashMap<>();


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveUserFormData() {

        userDataMap.put(USERNAME_INTERNAL,USERNAME_INTERNAL_VALUE);
        userDataMap.put(FIRSTNAME_INTERNAL,FIRSTNAME_INTERNAL_VALUE);
        userDataMap.put(LASTNAME_INTERNAL,LASTNAME_INTERNAL_VALUE);
        userDataMap.put(MOBILE_NUMBER_INTERNAL,MOBILE_NUMBER_INTERNAL_VALUE);
        userDataMap.put(DEPARTMENT_INTERNAL,DEPARTMENT_INTERNAL_VALUE);
        userDataMap.put(SIGNATURE_INTERNAL,SIGNATURE_INTERNAL_VALUE);

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
        InternalUserFormDataSchema internalUserFormDataSchema = new InternalUserFormDataSchema();
        internalUserFormDataSchema.setUserData(userDataMap);
        internalUserFormDataSchema.setRealmId("master");
        internalUserFormDataSchema.setVersion("1");

        when(objectMapper.convertValue(any(),ArgumentMatchers.eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition1);
        when(objectMapper.convertValue(any(),ArgumentMatchers.eq(UserData.class))).thenReturn(userData);
        when(userServiceImpl.validateUniqueConstraint(any())).thenReturn(List.of());
        testSaveUser();
        when(userFormDataRepository.save(any())).thenReturn(userFormDataDefinition1.withId(BigInteger.TWO));
        when(objectMapper.convertValue(any(),ArgumentMatchers.eq(InternalUserFormDataSchema.class))).thenReturn(internalUserFormDataSchema);
        InternalUserFormDataSchema result = internalUserFormDataServiceImpl.saveUserFormData("signature", internalUserFormDataSchema);
        Assertions.assertNotNull(result);
    }

    @Test
    void testSaveUser() {

        UserDefinition userDefinition = new UserDefinition();
        userDefinition.setUserName(USERNAME_INTERNAL_VALUE);
        userDefinition.setEmailId(USERNAME_INTERNAL_VALUE);
        userDefinition.setFirstName(FIRSTNAME_INTERNAL_VALUE);
        userDefinition.setLastName(LASTNAME_INTERNAL_VALUE);
        userDefinition.setMobileNumber(MOBILE_NUMBER_INTERNAL_VALUE);
        userDefinition.setDepartment(DEPARTMENT_INTERNAL_VALUE);
        userDefinition.setId(BigInteger.TWO);

        when(objectMapper.convertValue(any(), ArgumentMatchers.eq(UserDefinition.class))).thenReturn(userDefinition);
        when(userServiceImpl.validateUniqueConstraint(any())).thenReturn(List.of());
        when(userDefinitionRepository.save(any())).thenReturn(userDefinition);

        UserDefinition result = internalUserFormDataServiceImpl.saveUser(new UserData(null, USERNAME_INTERNAL_VALUE, FIRSTNAME_INTERNAL_VALUE, LASTNAME_INTERNAL_VALUE, MOBILE_NUMBER_INTERNAL_VALUE, USERNAME_INTERNAL_VALUE, DEPARTMENT_INTERNAL_VALUE));
        Assertions.assertNotNull(result);
    }
}
