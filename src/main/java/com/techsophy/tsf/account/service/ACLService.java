package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.CheckACLSchema;
import com.techsophy.tsf.account.entity.ACLDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.nio.file.AccessDeniedException;

public interface ACLService
{
    ACLSchema saveACL(ACLSchema aclSchema) throws JsonProcessingException;

    Page<ACLDefinition> getAllACLs(Pageable pageable);
    ACLSchema getACLById(String id);

    ACLValidate checkACLAccess(String id, CheckACLSchema checkACLSchema) throws JsonProcessingException, AccessDeniedException;
}
