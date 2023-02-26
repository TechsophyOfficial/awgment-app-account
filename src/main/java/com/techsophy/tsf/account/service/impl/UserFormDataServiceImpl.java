package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.exception.UserFormDataNotFoundException;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.FORM_NOT_FOUND_EXCEPTION;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserFormDataServiceImpl implements UserFormDataService
{
    private final UserFormDataDefinitionRepository userFormDataRepository;
    private final UserServiceImpl userServiceImpl;
    private final ObjectMapper objectMapper;
    private final GlobalMessageSource globalMessageSource;
    private final IdGeneratorImpl idGenerator;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;

    @Override
    public UserFormDataSchema saveUserFormData(UserFormDataSchema userFormDataSchema)
    {
        try
        {
            UserFormDataDefinition userFormDataDefinition = this.objectMapper
                    .convertValue(userFormDataSchema,UserFormDataDefinition.class);
            UserData userData = this.objectMapper.convertValue(userFormDataSchema.getUserData(),UserData.class);
            userDetails.userNameValidations(userData.getUserName());
            String userId = userFormDataSchema.getUserId();
            userData.setUserName(userData.getUserName().toLowerCase());
            Map<String,Object> loggedInUser = userServiceImpl.getCurrentlyLoggedInUserId().get(0);
            if (userId == null)
            {
                userFormDataDefinition.setId(idGenerator.nextId());
                userFormDataDefinition.setCreatedOn(Instant.now());
                userFormDataDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
                userFormDataDefinition.setVersion(1);

            }
            else
            {
                UserFormDataDefinition existingFormDataDefinition =
                        this.userFormDataRepository.findByUserId(BigInteger.valueOf(Long.parseLong(userId)))
                                .orElseThrow(() -> new UserFormDataNotFoundException(FORM_NOT_FOUND_EXCEPTION,globalMessageSource.get(FORM_NOT_FOUND_EXCEPTION,userId)));
                userFormDataDefinition.setId(existingFormDataDefinition.getId());
                userFormDataDefinition.setCreatedOn(existingFormDataDefinition.getCreatedOn());
                userFormDataDefinition.setCreatedById(existingFormDataDefinition.getCreatedById());
                userFormDataDefinition.setVersion(existingFormDataDefinition.getVersion() + 1);
                userData.setId(userId);
            }
            userFormDataDefinition.setUpdatedOn(Instant.now());
            userFormDataDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
            UserDefinition userDefinition = this.userServiceImpl.saveUser(userData);
            userFormDataDefinition.setUserId(userDefinition.getId());
            userFormDataDefinition.getUserData().put(USER_DATA_NAME,userFormDataDefinition.getUserData().get(USER_DATA_NAME).toString().toLowerCase());
            userFormDataDefinition = this.userFormDataRepository.save(userFormDataDefinition);
            return this.objectMapper.convertValue(userFormDataDefinition, UserFormDataSchema.class);
        }
        catch (ConstraintViolationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RunTimeException(e.getMessage());
        }
    }

    @Override
    public AuditableData getUserFormDataByUserId(String userId, Boolean onlyMandatoryFields)
    {
        if (onlyMandatoryFields == null || !onlyMandatoryFields)
        {
            UserFormDataDefinition userFormDataDefinition = this.userFormDataRepository.findByUserId(BigInteger.valueOf(Long.parseLong(userId)))
                    .orElseThrow(() -> new UserFormDataNotFoundException(FORM_NOT_FOUND_EXCEPTION,globalMessageSource.get(FORM_NOT_FOUND_EXCEPTION,userId)));
            return this.objectMapper.convertValue(userFormDataDefinition,UserFormDataSchema.class);
        }
        else
        {
            return this.userServiceImpl.getUserById(userId);
        }
    }

    public List getAllUserFormDataObjects(Boolean onlyMandatoryFields, String q, Sort sort)
    {
        if (onlyMandatoryFields == null || !onlyMandatoryFields)
        {
            if(isEmpty(q))
            {
                return this.userFormDataRepository.findAll(sort).stream()
                        .map(this::convertEntityToDTO).collect(Collectors.toList());
            }
            return this.userFormDataRepository.findFormDataUserByQSort(q,sort).stream()
                    .map(this::convertEntityToDTO).collect(Collectors.toList());
        }
        else
        {
            return this.userServiceImpl.getAllUsers(q,sort);
        }
    }

    @Override
    public PaginationResponsePayload getAllUserFormDataObjects(Boolean onlyMandatoryFields, String q, Pageable pageable)
    {
        if (onlyMandatoryFields == null || !onlyMandatoryFields)
        {
            if(isEmpty(q))
            {
                Page<UserFormDataDefinition> userFormDataDefinitionPage = this.userFormDataRepository.findAll(pageable);
                List<Map<String, Object>> userFormDataSchemaList = userFormDataDefinitionPage.stream()
                        .map(this::convertEntityToMap).collect(Collectors.toList());
                return tokenUtils.getPaginationResponsePayload(userFormDataDefinitionPage, userFormDataSchemaList);
            }
            Page<UserFormDataDefinition> userFormDataDefinitionPage=this.userFormDataRepository.findFormDataUserByQPageable(q,pageable);
            List<Map<String, Object>> userFormDataSchemaList = userFormDataDefinitionPage.stream()
                    .map(this::convertEntityToMap).collect(Collectors.toList());
            return tokenUtils.getPaginationResponsePayload(userFormDataDefinitionPage, userFormDataSchemaList);
        }
        else
        {
            return this.userServiceImpl.getAllUsers(q,pageable);
        }
    }

    @Override
    public List getAllUsersByFilter(Boolean onlyMandatoryFields, String filterColumn,String filterValue,Sort sort,String q)
    {
        if(Boolean.FALSE.equals(onlyMandatoryFields)&&isEmpty(q))
        {
                return this.userFormDataRepository.findByFilterColumnAndValue(sort,filterColumn,filterValue).stream()
                        .map(this::convertEntityToMap).collect(Collectors.toList());
        }
        return userServiceImpl.getAllUsersByFilter(filterColumn,filterValue);
    }

    @Override
    public PaginationResponsePayload getAllUsersByFilter(Boolean onlyMandatoryFields,String filterColumn,String filterValue,Pageable pageable, String q)
    {
        if(Boolean.FALSE.equals(onlyMandatoryFields)||onlyMandatoryFields==null)
        {
            Page<UserFormDataDefinition> userFormDataDefinitionPage = this.userFormDataRepository.findByFilterColumnAndValue(filterColumn,filterValue,pageable,q);
            List<Map<String, Object>> userFormDataSchemaList = userFormDataDefinitionPage.stream()
                    .map(this::convertEntityToMap).collect(Collectors.toList());
            return tokenUtils.getPaginationResponsePayload(userFormDataDefinitionPage, userFormDataSchemaList);
        }
        Page<UserFormDataDefinition> userFormDataDefinitionPage = this.userFormDataRepository.findByFilterColumnAndValue(filterColumn,filterValue,pageable,q);
        List<UserFormDataDefinition> userFormDataDefinitionList=userFormDataDefinitionPage.getContent();
       List<Map<String,Object>> userFormDataList=new ArrayList<>();
        for(UserFormDataDefinition userFormDataDefinition: userFormDataDefinitionList)
        {
            Map<String,Object> userMap=userFormDataDefinition.getUserData();
            userMap.put(ID,userFormDataDefinition.getId());
            userMap.put(USER_ID,userFormDataDefinition.getUserId());
            userMap.put("version",userFormDataDefinition.getVersion());
            userMap.put(CREATED_BY_ID,userFormDataDefinition.getCreatedById());
            userMap.put(CREATED_ON,userFormDataDefinition.getCreatedOn());
            userMap.put(UPDATED_BY_ID,userFormDataDefinition.getUpdatedById());
            userMap.put(UPDATED_ON,userFormDataDefinition.getUpdatedOn());
            userFormDataList.add(userMap);
        }
        return tokenUtils.getPaginationResponsePayload(userFormDataDefinitionPage,userFormDataList);
    }

    @Override
    public void deleteUserFormDataByUserId(String userId)
    {
        this.userServiceImpl.deleteUserById(userId);
        this.userFormDataRepository.deleteByUserId(BigInteger.valueOf(Long.parseLong(userId)));
    }

    public Map<String,Object> convertEntityToMap(UserFormDataDefinition userFormDataDefinition)
    {
        Map<String,Object> stringObjectMap=this.objectMapper.convertValue(userFormDataDefinition,Map.class);
        stringObjectMap.replace(ID,String.valueOf(stringObjectMap.get(ID)));
        stringObjectMap.replace(USER_ID,String.valueOf(stringObjectMap.get(USER_ID)));
        return stringObjectMap;
    }

    public UserDataSchema convertEntityToDTO(UserFormDataDefinition userFormDataDefinition)
    {
        return this.objectMapper.convertValue(userFormDataDefinition, UserDataSchema.class);
    }
}
