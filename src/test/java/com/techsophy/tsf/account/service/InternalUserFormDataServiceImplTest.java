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
import com.techsophy.tsf.account.service.impl.InternalUserFormDataServiceImpl;
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

import static org.mockito.Mockito.*;

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
    InternalUserFormDataServiceImpl internalUserFormDataServiceImpl;
    Map<String,Object> userDataMap = new HashMap<>();


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveUserFormData() {

        userDataMap.put("userName","rupali.t@techsophy.com");
        userDataMap.put("firstName","rupali");
        userDataMap.put("lastName","tomar");
        userDataMap.put("mobileNumber","9893876404");
        userDataMap.put("department","IT");
        userDataMap.put("signature","dfa0cy4+wono+hT939Sn09rPOeEG06R0rCEpAzYIQjKkX9t9LzBuAeowBN9AS4rHFhUpvpGPOPKQnY0jDAZimz2ZTCMff1hZqZvllncgA8BGmgPI46JXTlfKTFi4zwHYZ9AAR/C5Rt5H5VO4ftQ26HU9cioRnH+Skk65stGjvBz6QjwsRnqNne56gzvQnEegrY6CO28LUSUpTv3d6NXe15B+YsUvncLF86OnTsHQ9rB5zaaFcG0y3q7gciI0fbFeOBjgVmsA07reIA3rEnyNxJgsuNxFIVjlY59/1ITg5MBbmjM7VIcbN6fS83vQeHmQJFAFzoM5MIFYI9bWPWndbugxlzl9JfIq+5zmIbBDEPUAfozPPqE+0vGQE3vupDJAI13RWC9GK4tbeev2SmCWaVTXHVbBN2MTwc/+2aBbfGbUcc/sNG2dJVvvYaahHXp+X1RgGyLelt5pAso/5swHv64S5G+L1gvncONGTBMElmXjsbkOqcFiqTAnhocHq3AizbeWLONDYcBkqFi+0Vf6fsG2yHssVyS6nce9TxPE/BDR90JfDS1kFUTb4GaqRFVuN9579mLqrsNa1RZCruZQT3ejFGffTLUbPg/ENXnkmIhS7YTMSOuCUv8tTc9xGktcfb1BbAvjnfT7ukKSMa3KTQuq0ALHxBYQxOUIqslDIXU=");
        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition();
        userFormDataDefinition1.setUserData(userDataMap);
        userFormDataDefinition1.setUserId(BigInteger.TWO);
        userFormDataDefinition1.setVersion(1);
        UserData userData = new UserData();
        userData.setUserName("rupali.t@techsophy.com");
        userData.setFirstName("rupali");
        userData.setLastName("tomar");
        userData.setMobileNumber("9893876404");
        userData.setEmailId("rupali.t@techsophy.com");
        when(objectMapper.convertValue(any(),ArgumentMatchers.eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition1);
        when(objectMapper.convertValue(any(),ArgumentMatchers.eq(UserData.class))).thenReturn(userData);
        when(userServiceImpl.validateUniqueConstraint(any())).thenReturn(List.of());
        testSaveUser();
        when(userFormDataRepository.save(any())).thenReturn(userFormDataDefinition1.withId(BigInteger.TWO));
        when(objectMapper.convertValue(any(),ArgumentMatchers.eq(InternalUserFormDataSchema.class))).thenReturn(new InternalUserFormDataSchema(userDataMap, "master", "1"));
        InternalUserFormDataSchema result = internalUserFormDataServiceImpl.saveUserFormData("signature", new InternalUserFormDataSchema(userDataMap, "master", "1"));
        Assertions.assertNotNull(result);
    }

    @Test
    void testSaveUser() {

        UserDefinition userDefinition = new UserDefinition();
        userDefinition.setUserName("abc");
        userDefinition.setEmailId("abc@techsophy.com");
        userDefinition.setFirstName("ABC");
        userDefinition.setLastName("XYZ");
        userDefinition.setMobileNumber("9896767697");
        userDefinition.setId(BigInteger.TWO);

        when(objectMapper.convertValue(any(), ArgumentMatchers.eq(UserDefinition.class))).thenReturn(userDefinition);
        when(userServiceImpl.validateUniqueConstraint(any())).thenReturn(List.of());
        when(userDefinitionRepository.save(any())).thenReturn(userDefinition);

        UserDefinition result = internalUserFormDataServiceImpl.saveUser(new UserData(null, "userName", "firstName", "lastName", "mobileNumber", "emailId", "department"));
        Assertions.assertNotNull(result);
    }
}
