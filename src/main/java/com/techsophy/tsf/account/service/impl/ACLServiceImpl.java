package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.exception.EntityNotFoundByIdException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.repository.ACLRepository;
import com.techsophy.tsf.account.service.ACLService;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;

import java.math.BigInteger;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.*;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.ACL_NOT_FOUND_WITH_GIVEN_ID;
import static com.techsophy.tsf.account.constants.ErrorConstants.LOGGED_IN_USER_ID_NOT_FOUND;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
@NoArgsConstructor
public class ACLServiceImpl implements ACLService
{
    private  GlobalMessageSource globalMessageSource;
    private  IdGeneratorImpl idGenerator;
    private  ObjectMapper objectMapper;

    private  UserFormDataService userFormDataService;

    private  UserDetails userDetails;
    private  ACLRepository aclRepository;
    private  TokenUtils tokenUtils;
    @Value("${delimiter.left:<}")
    private  char delimiterStart;
    @Value("${delimiter.right:>}")
    private char delimiterEnd;


    @Override
    public ACLSchema saveACL(ACLSchema aclSchema) throws JsonProcessingException
    {
        Map<String,Object> loggedInUserDetails =userDetails.getUserDetails().get(0);
        if (isEmpty(String.valueOf(loggedInUserDetails.get(ID))))
        {
            throw new UserDetailsIdNotFoundException(LOGGED_IN_USER_ID_NOT_FOUND,globalMessageSource.get(LOGGED_IN_USER_ID_NOT_FOUND,loggedInUserDetails.get(ID).toString()));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(loggedInUserDetails.get(ID).toString()));
        ACLDefinition aclDefinition=this.objectMapper.convertValue(aclSchema,ACLDefinition.class);
        if(aclSchema.getId()!=null)
        {
            try {
                return aclRepository.updateACLDefinition(aclSchema,loggedInUserId);
            }
            catch (Exception e)
            {
                throw new DuplicateKeyException("Duplicate names are not allowed for ACL");
            }
        }
        BigInteger id=idGenerator.nextId();
        aclDefinition.setId(id);
        aclDefinition.setCreatedById(loggedInUserId);
        aclDefinition.setCreatedOn(Instant.now());
        try
        {
            aclRepository.save(aclDefinition);
        }
        catch (DuplicateKeyException e)
        {
            throw new DuplicateKeyException("Duplicate names are not allowed for ACL");
        }
        aclSchema.setId(String.valueOf(id));
        return aclSchema;
    }

    @Override
    public Page<ACLDefinition> getAllACLs(Pageable pageable)
    {
     return aclRepository.findAll(pageable);
    }

    @Override
    public ACLSchema getACLById(String id)
    {
       ACLDefinition aclDefinition=aclRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
               .orElseThrow(()->new EntityNotFoundByIdException(ACL_NOT_FOUND_WITH_GIVEN_ID,globalMessageSource.get(ACL_NOT_FOUND_WITH_GIVEN_ID,id)));
       return this.objectMapper.convertValue(aclDefinition,ACLSchema.class);
    }

    @Override
    public ACLValidate checkACLAccess(String id, CheckACLSchema checkACLSchema) throws JsonProcessingException, AccessDeniedException
    {
        Map<String,Object> userDetailsFromKeycloak= tokenUtils.getUserInformationMap(tokenUtils.getTokenFromContext());
        Optional<BigInteger> userId = userDetails.getUserId();
        AuditableData auditableData = new AuditableData();
        if(userId.isPresent())
        {
            auditableData  = userFormDataService.getUserFormDataByUserId(userId.get().toString(),false);
        }
        UserFormDataSchema userFormDataSchema = objectMapper.convertValue(auditableData,UserFormDataSchema.class);
        RequestProperties requestProperties = new RequestProperties();
        requestProperties.setUserInfo(userDetailsFromKeycloak);
        requestProperties.setContext(checkACLSchema.getContext());
        if(userFormDataSchema!=null&&userFormDataSchema.getUserData()!=null) {
        Map<String,Object> userData = userFormDataSchema.getUserData();
        requestProperties.setUserId(userFormDataSchema.getUserId());
        requestProperties.setUser(userData);
        requestProperties.setUsername((String) userData.get("userName"));
        }
        ACLDefinition aclDefinition=aclRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
                .orElseThrow(()->new EntityNotFoundByIdException(ACL_NOT_FOUND_WITH_GIVEN_ID,globalMessageSource.get(ACL_NOT_FOUND_WITH_GIVEN_ID,id)));
        ACLValidate aclValidate=new ACLValidate();
        aclValidate.setName(aclDefinition.getName());
        aclValidate.setRead(aclDefinition.getRead().stream()
                .map(x->x.evaluateDecision(requestProperties,checkACLSchema.getContext())).map(x->
                      renderAdditionalDetails(requestProperties,x)
                )
                .filter(Objects::nonNull).findFirst().orElse(new ACLDecision(UNDEFINED,null)));
        aclValidate.setUpdate(aclDefinition.getUpdate().stream()
                .map(x->x.evaluateDecision(requestProperties,checkACLSchema.getContext())).map(x->renderAdditionalDetails(requestProperties,x))
                .filter(Objects::nonNull).findFirst().orElse(new ACLDecision(UNDEFINED,null)));
       aclValidate.setDelete(aclDefinition.getDelete().stream()
               .map(x->x.evaluateDecision(requestProperties,checkACLSchema.getContext())).map(x->renderAdditionalDetails(requestProperties,x))
               .filter(Objects::nonNull).findFirst().orElse(new ACLDecision(UNDEFINED,null)));
        return  aclValidate;
    }
    private ACLDecision renderAdditionalDetails(RequestProperties request,ACLDecision aclDecision)
    {
        ObjectMapper mapper = new ObjectMapper();
        if(aclDecision!=null&&aclDecision.getAdditionalDetails()!=null) {
            ST detailTemplate = new ST(new JSONObject(aclDecision.getAdditionalDetails()).toString(), delimiterStart, delimiterEnd);
            detailTemplate.add("request", request);
            try {
                Map<String, Object> data = mapper.readValue(detailTemplate.render(), Map.class);
                aclDecision.setAdditionalDetails(data);
            } catch (JsonProcessingException e) {
                throw new RunTimeException(e.getMessage());
            }
        }
        return  aclDecision;
    }
}