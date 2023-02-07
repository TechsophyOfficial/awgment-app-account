package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.UserPreferencesController;
import com.techsophy.tsf.account.dto.ProfilePictureResponse;
import com.techsophy.tsf.account.dto.UserPreferencesResponse;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserPreferencesThemeService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserPreferencesControllerImplementation implements UserPreferencesController
{
    private final UserPreferencesThemeService userPreferencesThemeService;
    private final GlobalMessageSource globalMessageSource;

    @Override
    public ApiResponse<UserPreferencesResponse> saveUserPreferencesTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException
    {
        UserPreferencesResponse data= userPreferencesThemeService.saveUserPreferencesTheme(preferencesSchema);
        return new ApiResponse<>(data,true, globalMessageSource.get(USER_PREFERENCE_THEME_SAVED_SUCCESS));
    }

    @Override
    public ApiResponse<UserPreferencesResponse> saveUserWithDefaultTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException {
        UserPreferencesResponse data= userPreferencesThemeService.saveUserWithDefaultTheme(preferencesSchema);
        return new ApiResponse<>(data,true, globalMessageSource.get(USER_PREFERENCE_THEME_SAVED_SUCCESS));
    }

    @Override
    public ApiResponse<UserPreferencesSchema> getUserPreferencesThemesDataByUserId() throws IOException
    {
        userPreferencesThemeService.getUserPreferencesThemeByUserId();
        return new ApiResponse<>(userPreferencesThemeService.getUserPreferencesThemeByUserId(), true, globalMessageSource.get(GET_USER_PREFERENCE_THEME_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteUserPreferencesThemeDataByUserId() throws JsonProcessingException
    {
        userPreferencesThemeService.deleteUserPreferencesThemeByUserId();
        return  new ApiResponse<>(null,true, globalMessageSource.get(DELETE_USER_PREFERENCE_THEME_SUCCESS));
    }

    @Override
    public ApiResponse<ProfilePictureResponse> uploadProfilePictureByUserId(MultipartFile profilePicture) throws IOException
    {
        ProfilePictureResponse data= userPreferencesThemeService.uploadProfilePictureByUserId(profilePicture);
        return new ApiResponse<>(data,true, globalMessageSource.get(USER_PREFERENCE_PROFILE_PHOTO_SAVED_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteProfilePhotoByUserId() throws JsonProcessingException
    {
        userPreferencesThemeService.deleteProfilePictureByUserId();
        return  new ApiResponse<>(null,true, globalMessageSource.get(DELETE_USER_PROFILE_PICTURE_SUCCESS));
    }
}
