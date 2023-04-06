package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.InternalUserFormDataSchema;
import com.techsophy.tsf.account.dto.UserData;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.InternalUserFormDataService;
import com.techsophy.tsf.account.service.UserPreferencesThemeService;
import com.techsophy.tsf.account.utils.Rsa4096;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.EMAIL_ID_CANNOT_BE_CHANGED;
import static com.techsophy.tsf.account.constants.ErrorConstants.USER_NAME_CANNOT_BE_CHANGED;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class InternalUserFormDataServiceImpl implements InternalUserFormDataService
{
    private final UserFormDataDefinitionRepository userFormDataRepository;
    private final UserServiceImpl userServiceImpl;
    private final ObjectMapper objectMapper;
    private final GlobalMessageSource globalMessageSource;
    private final IdGeneratorImpl idGenerator;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;
    private final UserDefinitionRepository userDefinitionRepository;
    private final UserPreferencesThemeService userPreferencesThemeService;

    @Override
    public InternalUserFormDataSchema saveUserFormData(String signature, InternalUserFormDataSchema userFormDataSchema)
    {
        try
        {
            Rsa4096 rsa4096 = new Rsa4096();
            String sign = rsa4096.decryptFromBase64((String) userFormDataSchema.getUserData().get("signature"));
            String signatureValue = userFormDataSchema.getRealmId()+(String)userFormDataSchema.getUserData().get("userName");
            if(!sign.equals(signatureValue)) {
                throw new Exception("Un-authorized access");
            }
            UserFormDataDefinition userFormDataDefinition = this.objectMapper
                    .convertValue(userFormDataSchema,UserFormDataDefinition.class);
            UserData userData = this.objectMapper.convertValue(userFormDataSchema.getUserData(),UserData.class);
            userDetails.userNameValidations(userData.getUserName());

            //String realmId = userFormDataSchema.getRealmId();
            //TODO: Validate UserName within realmId
            //Current Document does not contains the Realm Id in UserFormDataDefinition
            //What Validation we need to put here for User Existence
            //If Exists throw Exception

//            UserFormDataDefinition existingFormDataDefinition =
//                    this.userFormDataRepository.findByUserId(BigInteger.valueOf(Long.parseLong(realmId)))
//                            .orElseThrow(() -> new UserFormDataNotFoundException(FORM_NOT_FOUND_EXCEPTION,globalMessageSource.get(FORM_NOT_FOUND_EXCEPTION,realmId)));


            userData.setUserName(userData.getUserName().toLowerCase());

            userFormDataDefinition.setId(idGenerator.nextId());
            userFormDataDefinition.setCreatedOn(Instant.now());
            //userFormDataDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
            userFormDataDefinition.setVersion(1);

            UserDefinition userDefinition = saveUser(userData);
            userFormDataDefinition.setUserId(userDefinition.getId());
            userFormDataDefinition.getUserData().put(USER_DATA_NAME,userFormDataDefinition.getUserData().get(USER_DATA_NAME).toString().toLowerCase());
            userFormDataDefinition = this.userFormDataRepository.save(userFormDataDefinition);
            return this.objectMapper.convertValue(userFormDataDefinition, InternalUserFormDataSchema.class);
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

    public UserDefinition saveUser(UserData userData)
    {
        try
        {
            UserDefinition userDefinition = this.objectMapper.convertValue(userData,UserDefinition.class);
            if (userData.getId() == null)
            {
                List<String> validationErrorMessages = userServiceImpl.validateUniqueConstraint(userDefinition);
                if (!validationErrorMessages.isEmpty())
                {
                    String errorMsg = String.join(COMMA,validationErrorMessages);
                    throw new InvalidInputException(errorMsg,errorMsg);
                }
                userDefinition.setId(idGenerator.nextId());
                userDefinition.setCreatedOn(Instant.now());
                userDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong("1001")));
            }
//            userDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
            userDefinition=this.userDefinitionRepository.save(userDefinition);
           /* if(userData.getId()==null) {
                Map<String, Object> map = new HashMap<>();
                map.put(THEME_ID, DEFAULT_THEME_ID);
                map.put(USER_ID, userDefinition.getId());
                UserPreferencesSchema userPreferencesSchema = this.objectMapper.convertValue(map, UserPreferencesSchema.class);
                userPreferencesThemeService.saveUserWithTheme(userPreferencesSchema);
            }*/
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
}
