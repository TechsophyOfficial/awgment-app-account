package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ProfilePictureResponse;
import com.techsophy.tsf.account.dto.UserPreferencesResponse;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface UserPreferencesThemeService
{
    UserPreferencesResponse saveUserPreferencesTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException;
    UserPreferencesResponse saveUserWithDefaultTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException;

    UserPreferencesSchema getUserPreferencesThemeByUserId() throws IOException;

    void deleteUserPreferencesThemeByUserId() throws JsonProcessingException;

    ProfilePictureResponse uploadProfilePictureByUserId(MultipartFile profilePicture) throws IOException;
    void deleteProfilePictureByUserId() throws JsonProcessingException;
}
