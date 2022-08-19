package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.io.IOException;
import java.io.InputStream;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@ChangeUnit(id = ADD_ADMIN_USER, order = ORDER_1, systemVersion = SYSTEM_VERSION_1)
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class AddAdminUser {
    private final MongoTemplate template;
    private final ObjectMapper objectMapper;

    @Execution
    public void changeSetFormDefinition() throws IOException {
        String pathUser =TP_USER_JSON ;
        String pathFormData =TP_FORMDATA_USER_JSON ;
        InputStream inputStreamUser = new ClassPathResource(pathUser).getInputStream();
        InputStream inputStreamFormData = new ClassPathResource(pathFormData).getInputStream();
        UserDefinition userDefinition = objectMapper.readValue(inputStreamUser, UserDefinition.class);
        UserFormDataDefinition userFormDataDefinition = objectMapper.readValue(inputStreamFormData, UserFormDataDefinition.class);
        Query query = new Query();
        long countTpUser = template.count(query, TP_USER_COLLECTION);
        long countTpFormDataUser = template.count(query, TP_FORM_DATA_USER_COLLECTION);

        if (countTpUser < 1 && countTpFormDataUser < 1) {
            template.save(userDefinition, TP_USER_COLLECTION);
            template.save(userFormDataDefinition, TP_FORM_DATA_USER_COLLECTION);
        }
    }
    @RollbackExecution
    public void rollback()
    {
        //Rollback Opertaion
    }
}
