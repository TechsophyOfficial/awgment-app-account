package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.InternalControllerImpl;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.Rsa4096;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import java.util.HashMap;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.InternalUserConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith({MockitoExtension.class})
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
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        userDataMap.put(USERNAME_INTERNAL,USERNAME_INTERNAL_VALUE);
        userDataMap.put(FIRSTNAME_INTERNAL,FIRSTNAME_INTERNAL_VALUE);
        userDataMap.put(LASTNAME_INTERNAL,LASTNAME_INTERNAL_VALUE);
        userDataMap.put(MOBILE_NUMBER_INTERNAL,MOBILE_NUMBER_INTERNAL_VALUE);
        userDataMap.put(DEPARTMENT_INTERNAL,DEPARTMENT_INTERNAL_VALUE);
    }

//    @Test
//    void testSaveUserFormDataForInternal() throws Exception{
//
//        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
//        HttpHeaders headers =new HttpHeaders();
//        headers.add("X-Signature", SIGNATURE_INTERNAL_VALUE);
//        Mockito.when(rsa4096.transform(anyString(),any())).thenReturn(userFormDataSchema);
//        Mockito.when(userFormDataService.saveUserFormData(any())).thenReturn(userFormDataSchema);
//        ApiResponse<UserFormDataSchema>userformDataSchemaExpected = new ApiResponse<>(userFormDataSchema,true,globalMessageSource.get(SAVE_FORM_SUCCESS));
//        Assertions.assertEquals(userformDataSchemaExpected,internalControllerImpl.saveUser(userFormDataSchema,headers));
//    }

    @Test
    void testSaveUserFormDataXSignatureNotFound() throws Exception
    {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,"1");
        HttpHeaders headers = new HttpHeaders();
        ApiResponse<UserFormDataSchema >userformDataSchemaExpected = new ApiResponse<>(null,false,globalMessageSource.get(SIGNATURE_MISSING));
        Assertions.assertEquals(userformDataSchemaExpected,internalControllerImpl.saveUser(userFormDataSchema,headers));
    }

//    @Test
//    void testSaveUserFormDataInvalid() throws Exception
//    {
//        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(null,null,"1");
//        HttpHeaders headers = new HttpHeaders();
//        headers.add("X-Signature", SIGNATURE_INTERNAL_VALUE);
//        Mockito.when(rsa4096.transform(anyString(), any(UserFormDataSchema.class))).thenReturn(userFormDataSchema);
//        Mockito.when(userFormDataService.saveUserFormData(userFormDataSchema)).thenThrow(BadRequestException.class);
//        Assertions.assertThrows(RuntimeException.class,()->internalControllerImpl.saveUser(userFormDataSchema,headers));
//    }


}
