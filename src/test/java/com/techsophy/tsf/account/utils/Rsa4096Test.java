package com.techsophy.tsf.account.utils;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.InvalidDataException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import java.security.*;
import java.util.HashMap;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.USERNAME_INTERNAL;
import static com.techsophy.tsf.account.constants.InternalUserConstants.*;
import static com.techsophy.tsf.account.constants.PropertyConstant.*;

@ExtendWith(MockitoExtension.class)
class Rsa4096Test
{
    @InjectMocks
    Rsa4096 rsa4096 = new Rsa4096(KEY_LOCATION_VALUE);
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    private KeyFactory keyFactory;
    @Mock
    private PublicKey publicKey;
    @Mock
    private Signature sign;
    Map<String,Object> userDataMap = new HashMap<>();

    @BeforeEach
    void setUp() throws InvalidKeyException {
        MockitoAnnotations.openMocks(this);
        sign.initVerify(publicKey);
        userDataMap.put(USERNAME_INTERNAL,USERNAME_INTERNAL_VALUE);
        userDataMap.put(FIRSTNAME_INTERNAL,FIRSTNAME_INTERNAL_VALUE);
        userDataMap.put(LASTNAME_INTERNAL,LASTNAME_INTERNAL_VALUE);
        userDataMap.put(MOBILE_NUMBER_INTERNAL,MOBILE_NUMBER_INTERNAL_VALUE);
        userDataMap.put(DEPARTMENT_INTERNAL,DEPARTMENT_INTERNAL_VALUE);
    }

    @Test
    void transformTestWrongSignatureException() throws SignatureException {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,String.valueOf(1));
        Mockito.when(rsa4096.verifySignature(SIGNATURE_INTERNAL_VALUE,USERNAME_INTERNAL_VALUE)).thenThrow(SignatureException.class);
        Assertions.assertThrows(RuntimeException.class,()->rsa4096.transform(SIGNATURE_INTERNAL_VALUE,userFormDataSchema));
    }

    @Test
    void transformTestSignatureNotVerifiedException() throws SignatureException {
        UserFormDataSchema userFormDataSchema = new UserFormDataSchema(userDataMap,null,String.valueOf(1));
        Mockito.when(rsa4096.verifySignature(SIGNATURE_INTERNAL_VALUE,USERNAME_INTERNAL_VALUE)).thenReturn(false);
        Assertions.assertThrows(InvalidDataException.class,()->rsa4096.transform(SIGNATURE_INTERNAL_VALUE,userFormDataSchema));
    }

    @Test
    void testSignatureVerified() throws SignatureException {
        Mockito.when(rsa4096.verifySignature(SIGNATURE_INTERNAL_VALUE, USERNAME_INTERNAL_VALUE)).thenReturn(true);
        Boolean result = rsa4096.verifySignature(SIGNATURE_INTERNAL_VALUE, USERNAME_INTERNAL_VALUE);
        Assertions.assertEquals(Boolean.TRUE, result);
    }
    @Test
    void testSignatureNotVerified() throws SignatureException {
        Mockito.when(rsa4096.verifySignature(SIGNATURE_INTERNAL_VALUE, "xyz@gmail.com")).thenReturn(false);
        Boolean result = rsa4096.verifySignature(SIGNATURE_INTERNAL_VALUE, "xyz@gmail.com");
        Assertions.assertEquals(Boolean.FALSE, result);
    }
    @Test
    void testInvalidKeyException() {
        Assertions.assertThrows(RuntimeException.class,()->new Rsa4096("test.pem"));
    }

}
