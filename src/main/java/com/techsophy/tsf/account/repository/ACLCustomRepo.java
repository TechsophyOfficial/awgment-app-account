package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.dto.ACLSchema;
import java.math.BigInteger;

public interface ACLCustomRepo
{
    ACLSchema updateACLDefinition(ACLSchema aclSchema, BigInteger loggedInUserId);
}
