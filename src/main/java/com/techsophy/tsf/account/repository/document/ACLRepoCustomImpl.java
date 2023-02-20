package com.techsophy.tsf.account.repository.document;

import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.repository.ACLRepoCustom;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.math.BigInteger;
import java.time.Instant;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@AllArgsConstructor
public class ACLRepoCustomImpl implements ACLRepoCustom
{
    private final MongoTemplate mongoTemplate;

    @Override
    public ACLSchema updateACLDefinition(ACLSchema aclSchema, BigInteger loggedInUserId)
    {
        Query query=new Query();
        query.addCriteria(Criteria.where(UNDERSCORE_ID).is(aclSchema.getId()));
        Update update=new Update()
                .set(NAME,aclSchema.getName())
                .set("read",aclSchema.getRead())
                .set("update",aclSchema.getUpdate())
                .set("delete",aclSchema.getDelete())
                .set("context",aclSchema.getContext())
                .set(UPDATED_BY_ID,loggedInUserId)
                .set(UPDATED_ON, Instant.now());
        mongoTemplate.updateFirst(query,update,ACLDefinition.class,TP_ACL);
        return aclSchema;
    }
}
