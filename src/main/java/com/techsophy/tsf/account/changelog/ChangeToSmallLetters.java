package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Change unit class for updating all user names to lowercase in MongoDB using Mongock.
 * This class updates user names in the UserDefinition and UserFormDataDefinition collections
 * while excluding the system user.
 */
@ChangeUnit(id = CHANGE_ALL_USER_NAMES_TO_LOWER, order = ORDER_4, systemVersion = SYSTEM_VERSION_1)
@RequiredArgsConstructor
@Slf4j
public class ChangeToSmallLetters {
    private final MongoTemplate template;
    private final ObjectMapper objectMapper;
    private final UserFormDataDefinitionRepository userFormDataRepository;
    private final UserDefinitionRepository userDefinitionRepository;
    private final MongoClient mongoClient;

    /**
     * Converts all user names to lowercase in the UserDefinition and UserFormDataDefinition collections,
     * excluding the system user.
     */
    @Execution
    public void changeSetFormDefinition() {
        template.updateMulti(Query.query(Criteria.where(USER_NAME_DATA).ne(SYSTEM)),
                AggregationUpdate.update().set(USER_NAME_DATA).toValue(StringOperators.ToLower.lowerValueOf(USER_NAME_DATA)),
                UserDefinition.class, TP_USER_COLLECTION);

        template.updateMulti(Query.query(Criteria.where(USER_DATA_USER_NAME).ne(SYSTEM)),
                AggregationUpdate.update().set(USER_DATA_USER_NAME).toValue(StringOperators.ToLower.lowerValueOf(USER_DATA_USER_NAME)),
                UserDefinition.class, TP_FORM_DATA_USER_COLLECTION);
    }

    /**
     * Logs an error message if the change set execution fails.
     */
    @RollbackExecution
    public void rollback() {
        log.info(EXECUTION_IS_FAILED);
    }
}
