package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.Rsa4096;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.SIGNATURE_MISSING;
import static com.techsophy.tsf.account.constants.AccountConstants.USERNAME_INTERNAL;
import static com.techsophy.tsf.account.constants.InternalUserConstants.*;
import static org.mockito.Mockito.*;

class InternalControllerImplTest {
    @Mock
    UserFormDataService userFormDataService;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    Rsa4096 rsa4096;
    @InjectMocks
    InternalControllerImpl internalControllerImpl;

    Map<String,Object> userDataMap = new HashMap<>();
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userDataMap.put(USERNAME_INTERNAL,USERNAME_INTERNAL_VALUE);
        userDataMap.put(FIRSTNAME_INTERNAL,FIRSTNAME_INTERNAL_VALUE);
        userDataMap.put(LASTNAME_INTERNAL,LASTNAME_INTERNAL_VALUE);
        userDataMap.put(MOBILE_NUMBER_INTERNAL,MOBILE_NUMBER_INTERNAL_VALUE);
        userDataMap.put(DEPARTMENT_INTERNAL,DEPARTMENT_INTERNAL_VALUE);
        ReflectionTestUtils.setField(internalControllerImpl, "keycloakPublicFile", "src/test/resources/testdata/public_key_rsa_4096_for_test.pem");
        internalControllerImpl.initializeRsa();
    }

    @Test
    void testSaveUser() throws JsonProcessingException {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
        HttpHeaders headers =new HttpHeaders();
        headers.add("X-Signature", SIGNATURE_INTERNAL_VALUE);
        when(userFormDataService.saveUserFormData(any())).thenReturn(userFormDataSchema);
        when(globalMessageSource.get(anyString())).thenReturn("getResponse");
        when(rsa4096.transform(anyString(), any())).thenReturn(userFormDataSchema);
        ApiResponse<UserFormDataSchema> result = internalControllerImpl.saveUser(userFormDataSchema, headers);
        Assertions.assertEquals(new ApiResponse<UserFormDataSchema>(userFormDataSchema,Boolean.TRUE,"getResponse"), result);
    }

    @Test
    void testSaveUserNoSignature() throws JsonProcessingException {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
        HttpHeaders headers =new HttpHeaders();
        when(userFormDataService.saveUserFormData(any())).thenReturn(userFormDataSchema);
        when(globalMessageSource.get(anyString())).thenReturn("getResponse");
        when(rsa4096.transform(anyString(), any())).thenReturn(userFormDataSchema);

        ApiResponse<UserFormDataSchema> result = internalControllerImpl.saveUser(userFormDataSchema, headers);
        Assertions.assertEquals(new ApiResponse<UserFormDataSchema>(null,Boolean.FALSE,"getResponse"), result);
    }

    @Test
    void testSaveUserRunTimeException() throws JsonProcessingException {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
        HttpHeaders headers =new HttpHeaders();
        headers.add("X-Signature", SIGNATURE_INTERNAL_VALUE);
        when(userFormDataService.saveUserFormData(any())).thenReturn(userFormDataSchema);
        when(globalMessageSource.get(anyString())).thenReturn("getResponse");
        when(rsa4096.transform(anyString(), any())).thenReturn(userFormDataSchema);

        RunTimeException thrown = Assertions.assertThrows(
                RunTimeException.class,
                () -> internalControllerImpl.saveUser(null, headers),
                "Invalid request"
        );
        Assertions.assertNotNull(thrown);
    }
    @Test
    void testSaveUserFormDataXSignatureNotFound() throws Exception
    {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
        HttpHeaders headers = new HttpHeaders();
        ApiResponse<UserFormDataSchema >userformDataSchemaExpected = new ApiResponse<>(null,false,globalMessageSource.get(SIGNATURE_MISSING));
        Assertions.assertEquals(userformDataSchemaExpected,internalControllerImpl.saveUser(userFormDataSchema,headers));
    }

}
