package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.nimbusds.jose.shaded.json.parser.ParseException;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.entity.UserPreferencesDefinition;
import com.techsophy.tsf.account.repository.UserPreferencesDefinitionRepository;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import static com.techsophy.tsf.account.constants.AccountConstants.TP_THEME_COLLECTION;
import static com.techsophy.tsf.account.constants.UserFormDataConstants.ANYSTRING;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.ONE;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AddThemeTest {
    @InjectMocks
    AddTheme addTheme;
    @Mock
    MongoTemplate template;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    MongoCollection mongoCollection;
    @Mock
    UserPreferencesDefinitionRepository userPreferencesDefinitionRepository;

    @Test
    void SystemUserTest() throws IOException, ParseException {
        Assertions.assertThrows(NullPointerException.class,()->addTheme.changeSetFormDefinition());
    }

    @Test
    void changeSetFormDefinitionWithNoDocumentTest() throws IOException, ParseException {
        UserPreferencesDefinition userPreferencesDefinition = new UserPreferencesDefinition();
        userPreferencesDefinition.setUserId(BigInteger.valueOf(1));
        userPreferencesDefinition.setId(BigInteger.valueOf(1));
        userPreferencesDefinition.setThemeId(BigInteger.valueOf(1));
        byte[] data=new byte[]{Byte.parseByte(ONE)};
        userPreferencesDefinition.setProfilePicture(new Binary(data));
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(ThemesDefinition.class))).thenReturn(themesDefinition);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(UserPreferencesDefinition.class))).thenReturn(userPreferencesDefinition);
        Mockito.when(template.getCollection(TP_THEME_COLLECTION)).thenReturn(mongoCollection);
        Mockito.when(userPreferencesDefinitionRepository.existsByUserId(any())).thenReturn(true);
        addTheme.changeSetFormDefinition();
        Mockito.verify(mockObjectMapper,Mockito.times(1)).readValue(any(InputStream.class), ArgumentMatchers.eq(ThemesDefinition.class));
    }

    @Test
    void changeSetFormDefinitionWithDocumentTest() throws IOException, ParseException {
        Document document = new Document();
        document.append("abc","value");
        document.append("key","obj");
        mongoCollection.insertOne(document);
        UserPreferencesDefinition userPreferencesDefinition = new UserPreferencesDefinition();
        userPreferencesDefinition.setUserId(BigInteger.valueOf(1));
        userPreferencesDefinition.setId(BigInteger.valueOf(1));
        userPreferencesDefinition.setThemeId(BigInteger.valueOf(1));
        byte[] data=new byte[]{Byte.parseByte(ONE)};
        userPreferencesDefinition.setProfilePicture(new Binary(data));
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(ThemesDefinition.class))).thenReturn(themesDefinition);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), ArgumentMatchers.eq(UserPreferencesDefinition.class))).thenReturn(userPreferencesDefinition);
        Mockito.when(template.getCollection(TP_THEME_COLLECTION)).thenReturn(mongoCollection);
        Mockito.when(userPreferencesDefinitionRepository.existsByUserId(any())).thenReturn(true);
        addTheme.changeSetFormDefinition();
        Mockito.verify(mockObjectMapper,Mockito.times(1)).readValue(any(InputStream.class), ArgumentMatchers.eq(ThemesDefinition.class));
    }

    @Test
    void rollbackTest(){
        addTheme.rollback();
        Assertions.assertTrue(true);

    }
}
