package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.entity.ACLDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.math.BigInteger;

public interface ACLRepository extends MongoRepository<ACLDefinition, BigInteger>,ACLRepoCustom
{

}
