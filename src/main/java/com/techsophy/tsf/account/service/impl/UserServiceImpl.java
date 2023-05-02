package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserData;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.exception.*;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.service.UserPreferencesThemeService;
import com.techsophy.tsf.account.service.UserService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import javax.validation.ConstraintViolationException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserServiceImpl implements UserService
{
    private final UserDefinitionRepository userDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final GlobalMessageSource globalMessageSource;
    private final IdGeneratorImpl idGenerator;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;
    private final Logger logger = Logger.getLogger("Inside UserServiceImpl");

    private final UserPreferencesThemeService userPreferencesThemeService;

    public UserDefinition saveUser(UserData userData)
    {
        try
        {
            UserDefinition userDefinition = this.objectMapper.convertValue(userData,UserDefinition.class);
            BigInteger loggedInUserId = userDetails.getCurrentAuditor().orElse(null);
            if (userData.getId() == null)
            {
                List<String> validationErrorMessages = validateUniqueConstraint(userDefinition);
                if (!validationErrorMessages.isEmpty())
                {
                    String errorMsg = String.join(COMMA,validationErrorMessages);
                    throw new InvalidInputException(errorMsg,errorMsg);
                }
                userDefinition.setId(idGenerator.nextId());
                userDefinition.setCreatedOn(Instant.now());
                userDefinition.setCreatedById(loggedInUserId);
            }
            else
            {
                /*checking whether UserDefinition exist with id or not */
                UserDefinition existingUserDefinition = getUserById(userDefinition.getId());
                userDefinition.setCreatedOn(existingUserDefinition.getCreatedOn());
                userDefinition.setCreatedById(existingUserDefinition.getCreatedById());
                /*cannot change userName and emailId*/
                if (!existingUserDefinition.getUserName().equalsIgnoreCase(userDefinition.getUserName()))
                {
                    throw new BadRequestException(USER_NAME_CANNOT_BE_CHANGED,globalMessageSource.get(USER_NAME_CANNOT_BE_CHANGED));
                }
                if (!existingUserDefinition.getEmailId().equalsIgnoreCase(userDefinition.getEmailId()))
                {
                    throw new BadRequestException(EMAIL_ID_CANNOT_BE_CHANGED,globalMessageSource.get(EMAIL_ID_CANNOT_BE_CHANGED));
                }
            }
            /*cannot change userName and emailId*/
            userDefinition.setUpdatedOn(Instant.now());
            userDefinition.setUpdatedById(loggedInUserId);
            userDefinition=this.userDefinitionRepository.save(userDefinition);
            if(userData.getId()==null) {
                Map<String, Object> map = new HashMap<>();
                map.put(THEME_ID, DEFAULT_THEME_ID);
                map.put(USER_ID, userDefinition.getId());
                UserPreferencesSchema userPreferencesSchema = this.objectMapper.convertValue(map, UserPreferencesSchema.class);
                logger.log(Level.SEVERE, "userPreferencesSchema in UserServiceImpl: {0} ", userPreferencesSchema);
                userPreferencesThemeService.saveUserWithTheme(userPreferencesSchema);
            }
            return userDefinition;
        }
        catch (ConstraintViolationException | BadRequestException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RunTimeException(e.getMessage());
        }
    }

    @Override
    public AuditableData getUserById(String id)
    {
        UserDefinition userDefinition = this.userDefinitionRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
                .orElseThrow(() -> new EntityNotFoundByIdException(USER_NOT_FOUND_BY_ID,globalMessageSource.get(USER_NOT_FOUND_BY_ID,id)));
        return  this.objectMapper.convertValue(userDefinition,UserData.class);
    }

    public List<UserData> getAllUsers(String q, Sort sort)
    {
        if(isEmpty(q))
        {
            return this.userDefinitionRepository.findAllUsers(sort).stream()
                    .map(userDefinition -> this.objectMapper.convertValue(userDefinition, UserData.class)
                    ).collect(Collectors.toList());

        }
        return this.userDefinitionRepository.findUserByQSort(q,sort).stream()
                .map(userDefinition -> this.objectMapper.convertValue(userDefinition, UserData.class)
                ).collect(Collectors.toList());
    }

    @Override
    public PaginationResponsePayload getAllUsers(String q, Pageable pageable)
    {
        if(isEmpty(q))
        {
            Page<UserDefinition> userDefinitionPage = this.userDefinitionRepository.findAllUsers(pageable);
            List<Map<String,Object>> userSchemaList = userDefinitionPage.stream()
                    .map(this::convertEntityToMap).collect(Collectors.toList());
            return tokenUtils.getPaginationResponsePayload(userDefinitionPage, userSchemaList);
        }
        Page<UserDefinition> userDefinitionPage = this.userDefinitionRepository.findUserByQPageable(q,pageable);
        List<Map<String,Object>> userSchemaList = userDefinitionPage.stream()
                .map(this::convertEntityToMap).collect(Collectors.toList());
        return tokenUtils.getPaginationResponsePayload(userDefinitionPage, userSchemaList);
    }

    @Override
    public UserData getUserByLoginId(String loginId)
    {
        UserDefinition userDefinition=this.userDefinitionRepository.findByEmailIdOrUserName(loginId, loginId).orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_BY_EMAIL_EXCEPTION,globalMessageSource.get(USER_NOT_FOUND_BY_EMAIL_EXCEPTION,loginId)));
        return this.objectMapper.convertValue(userDefinition, UserData.class);
    }

    public UserDefinition getUserById(BigInteger id)
    {
        return this.userDefinitionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundByIdException(USER_NOT_FOUND_BY_ID,globalMessageSource.get(USER_NOT_FOUND_BY_ID,id)));
    }
    @Override
    public void deleteUserById(String id)
    {
        if (!userDefinitionRepository.existsById(BigInteger.valueOf(Long.parseLong(id))))
        {
            throw new EntityNotFoundByIdException(USER_NOT_FOUND_BY_ID,globalMessageSource.get(USER_NOT_FOUND_BY_ID,id));
        }
        this.userDefinitionRepository.deleteById(BigInteger.valueOf(Long.parseLong(id)));
    }

    public List<String> validateUniqueConstraint(UserDefinition userDefinition)
    {
        List<String> validationErrorMessages = new ArrayList<>();
        String userName = userDefinition.getUserName();
        if (this.userDefinitionRepository.existsByUserName(userName))
        {
            validationErrorMessages.add(globalMessageSource.get(USER_NAME_ALREADY_EXISTS));
        }
        String emailId = userDefinition.getEmailId();
        if (this.userDefinitionRepository.existsByEmailId(emailId))
        {
            validationErrorMessages.add(EMAIL_ID_ALREADY_EXISTS);
        }
        return validationErrorMessages;
    }

    public List<AuditableData> getAllUsersByFilter(String filterColumn, String filterValue)
    {
        List<AuditableData> list = new ArrayList<>();
        if (StringUtils.hasText(filterColumn) && StringUtils.hasText(filterValue))
        {
            if(filterValue.startsWith(SERVICE_ACCOUNT))
            {
                UserData userSchema=getUserByLoginId(SYSTEM);
                list.add(userSchema);
            }
            else if (validFilterFields().contains(filterColumn.toUpperCase()))
            {
                filterValue=Character.isWhitespace(filterValue.charAt(0))&&filterValue.trim().matches("\\d")?"+"+filterValue.trim():filterValue;
                Optional<UserDefinition> userDefinitionOptional = this.userDefinitionRepository.findByEmailIdOrUserName(filterValue, filterValue);
                if (userDefinitionOptional.isPresent())
                {
                    UserData userSchema = this.objectMapper.convertValue(userDefinitionOptional.get(), UserData.class);
                    list.add(userSchema);
                }
            }
            else
            {
                throw new InvalidInputException(BASED_FILTERING_NOT_SUPPORTED,globalMessageSource.get(filterColumn + BASED_FILTERING_NOT_SUPPORTED));
            }
        }
        else
        {
            throw new InvalidInputException(FILTER_NOT_EMPTY,globalMessageSource.get(FILTER_NOT_EMPTY));
        }
        return list;
    }

    private List<String> validFilterFields()
    {
        return List.of(LOGIN_ID);
    }

    public List<Map<String,Object>> getCurrentlyLoggedInUserId() throws JsonProcessingException {
        String loginId = tokenUtils.getLoggedInUserId();
        if (loginId.startsWith(SERVICE_ACCOUNT))
        {
            return List.of(convertSchemaToMap(getUserByLoginId(SYSTEM)));
        }
        if (loginId.isEmpty())
        {
            throw new InvalidInputException(LOGGED_IN_USER_NOT_FOUND,globalMessageSource.get(LOGGED_IN_USER_NOT_FOUND));
        }
        return userDetails.getUserDetails();
    }

    public Map<String,Object> convertSchemaToMap(UserData userSchema)
    {
        Map<String,Object> stringObjectMap=this.objectMapper.convertValue(userSchema,Map.class);
        stringObjectMap.replace(ID,stringObjectMap.get(ID).toString());
        return stringObjectMap;
    }

    public Map<String,Object> convertEntityToMap(UserDefinition userDefinition)
    {
        Map<String,Object> stringObjectMap=this.objectMapper.convertValue(userDefinition,Map.class);
        stringObjectMap.replace(ID,stringObjectMap.get(ID).toString());
        return stringObjectMap;
    }
}