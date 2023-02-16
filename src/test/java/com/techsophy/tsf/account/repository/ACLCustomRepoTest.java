package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.repository.document.ACLRepoCustomImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.math.BigInteger;

@ExtendWith(MockitoExtension.class)
class ACLCustomRepoTest
{
    @Mock
    MongoTemplate mockMongoTemplate;
    @Mock
    ACLDefinition mockACLDefinition;
    @InjectMocks
    ACLRepoCustomImpl mockACLCustomRepoImpl;

    @Test
    void updateACLDefinitionTest()
    {
        ACLSchema aclSchema=new ACLSchema();
        Assertions.assertNotNull(mockACLCustomRepoImpl.updateACLDefinition(aclSchema, BigInteger.valueOf(101)));
    }
}
