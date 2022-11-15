package com.techsophy.tsf.account.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.domain.Sort;
import java.io.IOException;
import java.util.List;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.EXCEUTION_IS_FAILED;

@ChangeUnit(id = CHANGE_ALL_USER_NAMES_TO_LOWER, order = ORDER_4, systemVersion = SYSTEM_VERSION_1)
@AllArgsConstructor(onConstructor_ = {@Autowired})
@Slf4j
public class ChangeToSmallLetters {

    private final ObjectMapper objectMapper;
    private final UserFormDataDefinitionRepository userFormDataRepository;
    private final UserDefinitionRepository userDefinitionRepository;

    @Execution
    public void changeSetFormDefinition() throws IOException {
       List<UserFormDataDefinition> list = userFormDataRepository.findAll(Sort.by(DESCENDING));
        list.stream().forEach(data -> {
            String userName = data.getUserData().get(USER_DATA_NAME).toString().toLowerCase();
            data.getUserData().put(USER_DATA_NAME,userName);
            UserFormDataDefinition userFormDataDefinition = objectMapper.convertValue(data,UserFormDataDefinition.class);
            userFormDataRepository.save(userFormDataDefinition);
        });
        List<UserDefinition> userData = userDefinitionRepository.findAllUsers(Sort.by(DESCENDING));
        userData.stream().forEach(data -> {
            String userName = data.getUserName().toString().toLowerCase();
            data.setUserName(userName);
            UserDefinition userDefinition = objectMapper.convertValue(data,UserDefinition.class);
            userDefinitionRepository.save(userDefinition);
        });
    }

    @RollbackExecution
    public void rollback() {
        log.info(EXCEUTION_IS_FAILED);
    }
}
