package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.IOException;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.EXCEUTION_IS_FAILED;

@ChangeUnit(id = CHANGE_ALL_USER_NAMES_TO_LOWER, order = ORDER_4, systemVersion = SYSTEM_VERSION_1)
@AllArgsConstructor(onConstructor_ = {@Autowired})
@Slf4j
public class ChangeToSmallLetters {
    private final MongoTemplate template;
    private final ObjectMapper objectMapper;
    private final UserFormDataDefinitionRepository userFormDataRepository;
    private final UserDefinitionRepository userDefinitionRepository;
    private final MongoClient mongoClient;


    @Execution
    public void changeSetFormDefinition() {
        template.updateMulti(Query.query(Criteria.where(USER_NAME_DATA).ne(SYSTEM)),
                AggregationUpdate.update().set(USER_NAME_DATA).toValue(StringOperators.ToLower.lowerValueOf(USER_NAME_DATA)), UserDefinition.class, TP_USER_COLLECTION);
        template.updateMulti(Query.query(Criteria.where(USER_DATA_USER_NAME).ne(SYSTEM)),
                AggregationUpdate.update().set(USER_DATA_USER_NAME).toValue(StringOperators.ToLower.lowerValueOf(USER_DATA_USER_NAME)), UserDefinition.class, TP_FORM_DATA_USER_COLLECTION);
    }

    @RollbackExecution
    public void rollback() {
        log.info(EXCEUTION_IS_FAILED);
    }
}
