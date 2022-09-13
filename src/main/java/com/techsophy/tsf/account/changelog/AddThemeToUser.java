package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.shaded.json.parser.ParseException;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.entity.UserPreferencesDefinition;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.io.InputStream;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.EXCEUTION_IS_FAILED;

@ChangeUnit(id = ADD_THEME_TO_USER, order = ORDER_5, systemVersion = SYSTEM_VERSION_1)
@AllArgsConstructor(onConstructor_ = {@Autowired})
@Slf4j
public class AddThemeToUser {
    private  final MongoTemplate template;
    private final ObjectMapper objectMapper;
    @Execution
    public void changeSetFormDefinition() throws IOException, ParseException {
        String pathUser = TP_USER_THEME_JSON;
        InputStream inputStreamTest = new ClassPathResource(pathUser).getInputStream();
        UserPreferencesDefinition userPreferencesDefinition = objectMapper.readValue(inputStreamTest, UserPreferencesDefinition.class);
        template.save(userPreferencesDefinition,TP_USER_PREFERENCE_COLLECTION);
    }
    @RollbackExecution
    public void rollback()
    {
        log.info(EXCEUTION_IS_FAILED);
    }
}
