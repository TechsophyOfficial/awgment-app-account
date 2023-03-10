package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.ACLDecision;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.CheckACLSchema;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import static com.techsophy.tsf.account.constants.AccountConstants.ID;
import static com.techsophy.tsf.account.constants.AccountConstants.UNDEFINED;
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
        ACLDefinition aclDefinition=aclRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
                .orElseThrow(()->new EntityNotFoundByIdException(ACL_NOT_FOUND_WITH_GIVEN_ID,globalMessageSource.get(ACL_NOT_FOUND_WITH_GIVEN_ID,id)));
        ACLValidate aclValidate=new ACLValidate();
        aclValidate.setName(aclDefinition.getName());
        aclValidate.setRead(aclDefinition.getRead().stream()
                .map(x->x.evaluateDecision(userDetailsFromKeycloak,checkACLSchema.getContext()))
                        .filter(Objects::nonNull).findFirst().orElse(new ACLDecision(UNDEFINED,null)));
        aclValidate.setUpdate(aclDefinition.getUpdate().stream()
                .map(x->x.evaluateDecision(userDetailsFromKeycloak,checkACLSchema.getContext()))
                .filter(Objects::nonNull).findFirst().orElse(new ACLDecision(UNDEFINED,null)));
       aclValidate.setDelete(aclDefinition.getDelete().stream()
               .map(x->x.evaluateDecision(userDetailsFromKeycloak,checkACLSchema.getContext()))
               .filter(Objects::nonNull).findFirst().orElse(new ACLDecision(UNDEFINED,null)));
        return  aclValidate;
    }
}