package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.ProfilePictureResponse;
import com.techsophy.tsf.account.dto.UserPreferencesResponse;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.entity.UserPreferencesDefinition;
import com.techsophy.tsf.account.exception.*;
import com.techsophy.tsf.account.repository.UserPreferencesDefinitionRepository;
import com.techsophy.tsf.account.service.UserPreferencesThemeService;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonBinarySubType;
import org.bson.types.Binary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserPreferencesThemeServiceImplementation implements UserPreferencesThemeService
{
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            JPG_TYPE, MediaType.IMAGE_JPEG_VALUE,
            JPEG_TYPE, MediaType.IMAGE_JPEG_VALUE,
            PNG_TYPE, MediaType.IMAGE_PNG_VALUE);
    private final ObjectMapper objectMapper;
    private final UserPreferencesDefinitionRepository userPreferencesDefinitionRepository;
    private final GlobalMessageSource globalMessageSource;
    private final IdGeneratorImpl idGenerator;
    private final UserDetails userDetails;

    @Override
    public UserPreferencesResponse saveUserPreferencesTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException
    {
        String userId = (String) userDetails.getUserDetails().get(0).get(ID);
        if (StringUtils.isEmpty(userId))
        {
            throw new EntityIdNotFoundException(LOGGED_IN_USER_NOT_NULL,globalMessageSource.get(LOGGED_IN_USER_NOT_NULL, userId));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(userId));
        preferencesSchema.setUserId(String.valueOf(loggedInUserId));
        return saveUserWithTheme(preferencesSchema);
    }

    @Override
    public UserPreferencesResponse saveUserWithTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException {
        UserPreferencesDefinition userPreferenceData = new UserPreferencesDefinition();
        if(!userPreferencesDefinitionRepository.existsByUserId(BigInteger.valueOf(Long.parseLong(preferencesSchema.getUserId())))) {
            userPreferenceData.setId(idGenerator.nextId());
            userPreferenceData.setThemeId(BigInteger.valueOf(Long.parseLong(preferencesSchema.getThemeId())));
            userPreferenceData.setUserId(BigInteger.valueOf(Long.parseLong(preferencesSchema.getUserId())));
        }
        else
        {
            userPreferenceData=userPreferencesDefinitionRepository.findByUserId(BigInteger.valueOf(Long.parseLong(preferencesSchema.getUserId()))).orElseThrow(()->new UserPreferencesNotFoundByLoggedInUserIdException(USER_PREFERENCE_THEME_NOT_FOUND,globalMessageSource.get(USER_PREFERENCE_THEME_NOT_FOUND, preferencesSchema.getUserId())));
           userPreferenceData.setThemeId(BigInteger.valueOf(Long.parseLong(preferencesSchema.getThemeId())));
        }

        this.userPreferencesDefinitionRepository.save(userPreferenceData);
        return this.objectMapper.convertValue(userPreferenceData, UserPreferencesResponse.class);
    }
    @Override
    public UserPreferencesSchema getUserPreferencesThemeByUserId() throws IOException
    {
        String profilePicture=null;
        String userId = (String) userDetails.getUserDetails().get(0).get(ID);
        if (StringUtils.isEmpty(userId))
        {
            throw new EntityIdNotFoundException(LOGGED_IN_USER_NOT_NULL,globalMessageSource.get(LOGGED_IN_USER_NOT_NULL, userId));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(userId));

        UserPreferencesDefinition userPreferencesDefinition = this.userPreferencesDefinitionRepository.findByUserId(loggedInUserId)
                .orElseThrow(() -> new UserPreferencesNotFoundByLoggedInUserIdException(USER_PREFERENCE_THEME_NOT_FOUND,globalMessageSource.get(USER_PREFERENCE_THEME_NOT_FOUND, loggedInUserId)));
        if(userPreferencesDefinition.getProfilePicture()!=null)
        {
            profilePicture = Base64.getEncoder().encodeToString (userPreferencesDefinition.getProfilePicture().getData());
        }
        return new UserPreferencesSchema(String.valueOf(userPreferencesDefinition.getId()), String.valueOf(userPreferencesDefinition.getUserId()), String.valueOf(userPreferencesDefinition.getThemeId()), profilePicture);
    }

    @Override
    public void deleteUserPreferencesThemeByUserId() throws JsonProcessingException
    {
        String userId = (String) userDetails.getUserDetails().get(0).get(ID);
        if (StringUtils.isEmpty(userId))
        {
            throw new EntityIdNotFoundException(LOGGED_IN_USER_NOT_NULL,globalMessageSource.get(LOGGED_IN_USER_NOT_NULL, userId));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(userId));

        if (!userPreferencesDefinitionRepository.existsByUserId(loggedInUserId))
        {
            throw new UserPreferencesNotFoundByLoggedInUserIdException(ENTITY_NOT_FOUND_EXCEPTION,globalMessageSource.get(ENTITY_NOT_FOUND_EXCEPTION, loggedInUserId));
        }
        this.userPreferencesDefinitionRepository.deleteByUserId(loggedInUserId);
    }

    @Override
    public ProfilePictureResponse uploadProfilePictureByUserId(MultipartFile profilePicture) throws IOException
    {
        String userId = (String) userDetails.getUserDetails().get(0).get(ID);
        if (StringUtils.isEmpty(userId))
        {
            throw new EntityIdNotFoundException(LOGGED_IN_USER_NOT_NULL,globalMessageSource.get(LOGGED_IN_USER_NOT_NULL, userId));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(userId));

        if (profilePicture.isEmpty())
        {
            throw new ProfilePictureNotPresentException(PROFILE_PICTURE_CANNOT_BE_EMPTY,globalMessageSource.get(PROFILE_PICTURE_CANNOT_BE_EMPTY));
        }
        String fileName=profilePicture.getOriginalFilename();
        String fileExtension = FilenameUtils.getExtension(profilePicture.getOriginalFilename());
        if (SUPPORTED_TYPES.containsKey(fileExtension))
        {
            UserPreferencesDefinition userPreferenceData = new UserPreferencesDefinition();
            UserPreferencesDefinition userPreferencesDefinition;
            if (userPreferencesDefinitionRepository.existsByUserId(loggedInUserId))
            {
                userPreferencesDefinition = this.userPreferencesDefinitionRepository.findByUserId(loggedInUserId)
                        .orElseThrow(() -> new UserPreferencesNotFoundByLoggedInUserIdException(USER_PREFERENCE_THEME_NOT_FOUND,globalMessageSource.get(USER_PREFERENCE_THEME_NOT_FOUND, loggedInUserId)));
                userPreferenceData.setId(userPreferencesDefinition.getId());
                userPreferenceData.setUserId(userPreferencesDefinition.getUserId());
                userPreferenceData.setThemeId(userPreferencesDefinition.getThemeId());
            }
            log.info(FILE_UPLOAD_STARTED_WITH_NAME, fileName,fileName);

            userPreferenceData.setProfilePicture(new Binary(BsonBinarySubType.BINARY, profilePicture.getBytes()));

            this.userPreferencesDefinitionRepository.save(userPreferenceData);
            return this.objectMapper.convertValue(userPreferenceData, ProfilePictureResponse.class);
        }
        else
        {
            String msg = UPLOAD_VALID_FILE_TYPES + SUPPORTED_TYPES.keySet();
            log.error(msg);
            throw new InvalidProfilePictureException(msg,msg);
        }
    }

    @Override
    public void deleteProfilePictureByUserId() throws JsonProcessingException
    {
        String userId = (String) userDetails.getUserDetails().get(0).get(ID);
        if (StringUtils.isEmpty(userId))
        {
            throw new EntityIdNotFoundException(LOGGED_IN_USER_NOT_NULL,globalMessageSource.get(LOGGED_IN_USER_NOT_NULL, userId));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(userId));

        UserPreferencesDefinition userPreferenceData = new UserPreferencesDefinition();
        UserPreferencesDefinition userPreferencesDefinition = this.userPreferencesDefinitionRepository.findByUserId(loggedInUserId)
                .orElseThrow(() -> new UserPreferencesNotFoundByLoggedInUserIdException(USER_PROFILE_PICTURE_NOT_FOUND,globalMessageSource.get(USER_PROFILE_PICTURE_NOT_FOUND, loggedInUserId)));
        userPreferencesDefinition.setProfilePicture(null);
        userPreferenceData.setId(userPreferencesDefinition.getId());
        userPreferenceData.setUserId(userPreferencesDefinition.getUserId());
        userPreferenceData.setThemeId(userPreferencesDefinition.getThemeId());
        this.userPreferencesDefinitionRepository.save(userPreferenceData);
   }
}