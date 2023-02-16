package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.Decision;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.exception.EntityNotFoundByIdException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.repository.ACLRepository;
import com.techsophy.tsf.account.service.ACLService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.ACL_NOT_FOUND_WITH_GIVEN_ID;
import static com.techsophy.tsf.account.constants.ErrorConstants.LOGGED_IN_USER_ID_NOT_FOUND;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class ACLServiceImpl implements ACLService
{
    private final GlobalMessageSource globalMessageSource;
    private final IdGeneratorImpl idGenerator;
    private final ObjectMapper objectMapper;
    private final UserDetails userDetails;
    private final ACLRepository aclRepository;
    private final TokenUtils tokenUtils;

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
        aclDefinition.setUpdatedById(loggedInUserId);
        aclDefinition.setUpdatedOn(Instant.now());
        if(aclSchema.getId()!=null)
        {
            return aclRepository.updateACLDefinition(aclSchema,loggedInUserId);
        }
        BigInteger id=idGenerator.nextId();
        aclDefinition.setId(id);
        aclDefinition.setCreatedById(loggedInUserId);
        aclDefinition.setCreatedOn(Instant.now());
        aclRepository.save(aclDefinition);
        aclSchema.setId(String.valueOf(id));
        return aclSchema;
    }

    @Override
    public PaginationResponsePayload getAllACLs(Pageable pageable)
    {
        Page<ACLDefinition> aclDefinitionPage= aclRepository.findAll(pageable);
        List<Map<String,Object>> aclList=aclDefinitionPage.stream().map(this::convertTaskEntityToMap).collect(Collectors.toList());
        return tokenUtils.getPaginationResponsePayload(aclDefinitionPage,aclList);
    }

    public Map<String, Object> convertTaskEntityToMap(ACLDefinition aclDefinition)
    {
        return this.objectMapper.convertValue(aclDefinition, Map.class);
    }

    @Override
    public ACLSchema getACLById(String id)
    {
       ACLDefinition aclDefinition=aclRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
               .orElseThrow(()->new EntityNotFoundByIdException(ACL_NOT_FOUND_WITH_GIVEN_ID,globalMessageSource.get(ACL_NOT_FOUND_WITH_GIVEN_ID,id)));
       return this.objectMapper.convertValue(aclDefinition,ACLSchema.class);
    }

    @Override
    public ACLValidate checkACLAccess(String id) throws JsonProcessingException, AccessDeniedException
    {
        Map<String,Object> userDetailsFromKeycloak= tokenUtils.getUserInformationMap(tokenUtils.getTokenFromContext());
        ACLDefinition aclDefinition=aclRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
                .orElseThrow(()->new EntityNotFoundByIdException(ACL_NOT_FOUND_WITH_GIVEN_ID,globalMessageSource.get(ACL_NOT_FOUND_WITH_GIVEN_ID,id)));
        ACLValidate aclValidate=new ACLValidate();
        aclValidate.setName(aclDefinition.getName());
        List<Decision> readDecisionList=aclDefinition.getRead().stream().map(x->x.evaluateDecision(userDetailsFromKeycloak,null)).collect(Collectors.toList());
        if(readDecisionList.stream().anyMatch(Objects::nonNull))
        {
            aclValidate.setRead(getDecisionList(readDecisionList));
        }
        List<Decision> updateDecisionList=aclDefinition.getUpdate().stream().map(x->x.evaluateDecision(userDetailsFromKeycloak,null)).collect(Collectors.toList());
        if(updateDecisionList.stream().anyMatch(Objects::nonNull))
        {
            aclValidate.setUpdate(getDecisionList(updateDecisionList));
        }
        List<Decision> deleteDecisionList=aclDefinition.getDelete().stream().map(x->x.evaluateDecision(userDetailsFromKeycloak,null)).collect(Collectors.toList());
        if(deleteDecisionList.stream().anyMatch(Objects::nonNull))
        {
            aclValidate.setDelete(getDecisionList(deleteDecisionList));
        }
        return  aclValidate;
    }

    private static Decision getDecisionList(List<Decision> decisionsList)
    {
        return decisionsList.stream().filter(Objects::nonNull).findFirst().orElse(new Decision(UNDEFINED,null));
    }
}