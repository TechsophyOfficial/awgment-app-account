package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserPreferencesDefinition;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.io.InputStream;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.EXECUTION_IS_FAILED;

/**
 * Change unit class for adding themes and user preferences in MongoDB using Mongock.
 * This class reads theme and user preference data from JSON files and inserts it
 * into the database if no themes exist and the user is found in the database.
 */
@ChangeUnit(id = ADD_THEME, order = ORDER_3, systemVersion = SYSTEM_VERSION_1)
@RequiredArgsConstructor
@Slf4j
public class AddTheme {
    private final MongoTemplate template;
    private final ObjectMapper objectMapper;

    /**
     * Reads theme and user preference data from JSON files and inserts it into MongoDB
     * if no themes exist and the user associated with the preferences exists.
     *
     * @throws IOException if an error occurs while reading the JSON files.
     */
    @Execution
    public void changeSetFormDefinition() throws IOException {
        String pathTheme = TP_THEME_JSON;
        String pathThemeUser = TP_USER_THEME_JSON;
        InputStream inputStreamTest = new ClassPathResource(pathTheme).getInputStream();
        ThemesDefinition themesDefinition = objectMapper.readValue(inputStreamTest, ThemesDefinition.class);
        InputStream inputStreamTest1 = new ClassPathResource(pathThemeUser).getInputStream();
        UserPreferencesDefinition userPreferencesDefinition = objectMapper.readValue(inputStreamTest1, UserPreferencesDefinition.class);
        long count = template.getCollection(TP_THEME_COLLECTION).countDocuments();
        UserDefinition userDefinition = template.findById(userPreferencesDefinition.getUserId(), UserDefinition.class);
        if (count == 0 && userDefinition != null) {
            template.save(themesDefinition, TP_THEME_COLLECTION);
            template.save(userPreferencesDefinition, TP_USER_PREFERENCE_COLLECTION);
        }
    }

    /**
     * Logs an error message if the change set execution fails.
     */
    @RollbackExecution
    public void rollback() {
        log.info(EXECUTION_IS_FAILED);
    }
}
