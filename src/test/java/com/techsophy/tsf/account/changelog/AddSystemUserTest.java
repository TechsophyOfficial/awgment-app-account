package com.techsophy.tsf.account.changelog;

import com.nimbusds.jose.shaded.json.parser.ParseException;
import com.techsophy.tsf.account.entity.UserDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.io.IOException;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AddSystemUserTest {

    @InjectMocks
    AddSystemUser addSystemUser;
    @Mock
    MongoTemplate template;

    @Test
    void SystemUserTest() throws IOException, ParseException {
        Assertions.assertThrows(NullPointerException.class,()->addSystemUser.changeSetFormDefinition());
    }
}