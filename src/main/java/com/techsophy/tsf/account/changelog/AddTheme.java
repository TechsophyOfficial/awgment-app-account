package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.shaded.json.parser.ParseException;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.entity.UserDefinition;
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
import static com.techsophy.tsf.account.constants.ErrorConstants.EXECUTION_IS_FAILED;

@ChangeUnit(id = ADD_THEME, order = ORDER_3, systemVersion = SYSTEM_VERSION_1)
@AllArgsConstructor(onConstructor_ = {@Autowired})
@Slf4j
public class AddTheme {
    private  final MongoTemplate template;
    private final ObjectMapper objectMapper;

    @Execution
    public void changeSetFormDefinition() throws IOException {
        String pathTheme = TP_THEME_JSON;
        String pathThemeUser = TP_USER_THEME_JSON;
        InputStream inputStreamTest = new ClassPathResource(pathTheme).getInputStream();
        ThemesDefinition themesDefinition = objectMapper.readValue(inputStreamTest, ThemesDefinition.class);
        InputStream inputStreamTest1 = new ClassPathResource(pathThemeUser).getInputStream();
        UserPreferencesDefinition userPreferencesDefinition = objectMapper.readValue(inputStreamTest1, UserPreferencesDefinition.class);
        long count = template.getCollection(TP_THEME_COLLECTION).countDocuments();
        UserDefinition userDefinition = template.findById(userPreferencesDefinition.getUserId(),UserDefinition.class);
        if(count==0&&userDefinition!=null) {
            template.save(themesDefinition, TP_THEME_COLLECTION);
            template.save(userPreferencesDefinition,TP_USER_PREFERENCE_COLLECTION);
        }
     }
    @RollbackExecution
    public void rollback()
    {
        log.info(EXECUTION_IS_FAILED);
    }

}
