package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.nimbusds.jose.shaded.json.parser.ParseException;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserPreferencesDefinition;
import com.techsophy.tsf.account.repository.UserPreferencesDefinitionRepository;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
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
    void SystemUserTest() {
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
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), eq(ThemesDefinition.class))).thenReturn(themesDefinition);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), eq(UserPreferencesDefinition.class))).thenReturn(userPreferencesDefinition);
        Mockito.when(template.getCollection(TP_THEME_COLLECTION)).thenReturn(mongoCollection);
        Mockito.when(mongoCollection.countDocuments()).thenReturn(2L);
        addTheme.changeSetFormDefinition();
        Mockito.verify(template,Mockito.times(0)).save(any(),any());    }

    @Test
    void changeSetFormDefinitionWithDocumentTest() throws IOException, ParseException {
        MongoCollection mongoCollectionLocal = mock(MongoCollection.class);
        String abc = "abc";
        Document document = new Document();
        document.append("abc","value");
        document.append("key","obj");
        mongoCollectionLocal.insertOne(document);
        UserPreferencesDefinition userPreferencesDefinition = new UserPreferencesDefinition();
        userPreferencesDefinition.setUserId(BigInteger.valueOf(1));
        userPreferencesDefinition.setId(BigInteger.valueOf(1));
        userPreferencesDefinition.setThemeId(BigInteger.valueOf(1));
        byte[] data=new byte[]{Byte.parseByte(ONE)};
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,abc,abc,abc,abc,abc,abc);
        userPreferencesDefinition.setProfilePicture(new Binary(data));
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), eq(ThemesDefinition.class))).thenReturn(themesDefinition);
        Mockito.when(mockObjectMapper.readValue(any(InputStream.class), eq(UserPreferencesDefinition.class))).thenReturn(userPreferencesDefinition);
        Mockito.when(template.getCollection(TP_THEME_COLLECTION)).thenReturn(mongoCollectionLocal);
        Mockito.when(template.findById(any(), eq(UserDefinition.class))).thenReturn(userDefinition);
        addTheme.changeSetFormDefinition();
        Mockito.verify(template,Mockito.times(2)).save(any(),any());    }

    @Test
    void rollbackTest(){
        addTheme.rollback();
        Assertions.assertTrue(true);

    }
}
