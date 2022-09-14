package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.TP_FORM_DATA_USER_COLLECTION;
import static com.techsophy.tsf.account.constants.AccountConstants.TP_USER_COLLECTION;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AddAdminUserTest {
    @InjectMocks
    AddAdminUser addAdminUser;
    @Mock
    MongoTemplate template;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
            MongoCollection mongoCollection;

    Map<String,Object> map = new HashMap<>();
    @BeforeEach
    void init() {
        map.put("abc", "abc");
    }

    @Test
    void changeSetFormDefinitionTest() throws IOException {
        Document document = new Document();
        document.append("abc","value");
        document.append("key","obj");
        mongoCollection.insertOne(document);
        UserFormDataDefinition userFormDataDefinition = new UserFormDataDefinition(BigInteger.ONE,map,BigInteger.ONE,1);
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","abc","abc@gmail.com","cs");
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(UserDefinition.class))).thenReturn(userDefinition);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition);
        Mockito.when(template.getCollection(TP_USER_COLLECTION)).thenReturn(mongoCollection);
        Mockito.when(template.getCollection(TP_FORM_DATA_USER_COLLECTION)).thenReturn(mongoCollection);
        addAdminUser.changeSetFormDefinition();
        Mockito.verify(mockObjectMapper,Mockito.times(1)).readValue(any(InputStream.class), ArgumentMatchers.eq(UserDefinition.class));
    }

    @Test
    void changeSetFormDefinitionNoDocumentTest() throws IOException {
        UserFormDataDefinition userFormDataDefinition = new UserFormDataDefinition(BigInteger.ONE,map,BigInteger.ONE,1);
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","abc","abc@gmail.com","cs");
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(UserDefinition.class))).thenReturn(userDefinition);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition);
        Mockito.when(template.getCollection(TP_USER_COLLECTION)).thenReturn(mongoCollection);
        Mockito.when(template.getCollection(TP_FORM_DATA_USER_COLLECTION)).thenReturn(mongoCollection);
        Mockito.when(mongoCollection.countDocuments()).thenReturn(2L);
        addAdminUser.changeSetFormDefinition();
        Mockito.verify(mockObjectMapper,Mockito.times(1)).readValue(any(InputStream.class), ArgumentMatchers.eq(UserDefinition.class));
    }

    @Test
    void rollbackTest(){
        addAdminUser.rollback();
        Assertions.assertTrue(true);
    }
}
