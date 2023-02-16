package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import org.springframework.data.domain.Pageable;
import java.nio.file.AccessDeniedException;

public interface ACLService
{
    ACLSchema saveACL(ACLSchema aclSchema) throws JsonProcessingException;

    PaginationResponsePayload getAllACLs(Pageable pageable);
    ACLSchema getACLById(String id);

    ACLValidate checkACLAccess(String id) throws JsonProcessingException, AccessDeniedException;
}
