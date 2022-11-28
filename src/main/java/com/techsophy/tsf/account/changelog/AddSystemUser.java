package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.shaded.json.parser.ParseException;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.io.IOException;
import java.io.InputStream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.EXCEUTION_IS_FAILED;

@ChangeUnit(id = ADD_SYSTEM_USER, order = ORDER_2, systemVersion = SYSTEM_VERSION_1)
@AllArgsConstructor(onConstructor_ = {@Autowired})
@Slf4j
public class AddSystemUser {
    private  final MongoTemplate template;
    private final ObjectMapper objectMapper;
    public static  int count =0;
    @Execution
    public void changeSetFormDefinition() throws IOException {
        String pathUser =TP_SYSTEM_USER_JSON ;
        String pathFormData =TP_FORMDATA_SYSTEM_USER_JSON ;
        InputStream inputStreamUser = new ClassPathResource(pathUser).getInputStream();
        InputStream inputStreamFormData = new ClassPathResource(pathFormData).getInputStream();
        UserDefinition userDefinition = objectMapper.readValue(inputStreamUser, UserDefinition.class);
        UserFormDataDefinition userFormDataDefinition = objectMapper.readValue(inputStreamFormData, UserFormDataDefinition.class);
        long countTpUser = template.getCollection(TP_USER_COLLECTION).countDocuments();
        long countTpFormDataUser = template.getCollection(TP_FORM_DATA_USER_COLLECTION).countDocuments();
        if (countTpUser <= 1 && countTpFormDataUser <=1) {
            template.save(userDefinition, TP_USER_COLLECTION);
            template.save(userFormDataDefinition, TP_FORM_DATA_USER_COLLECTION);
        }
    }
    @RollbackExecution
    public void rollback()
    {
        log.info(EXCEUTION_IS_FAILED);
    }
}
