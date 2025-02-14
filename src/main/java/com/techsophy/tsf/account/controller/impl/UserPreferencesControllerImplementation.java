package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.UserPreferencesController;
import com.techsophy.tsf.account.dto.ProfilePictureResponse;
import com.techsophy.tsf.account.dto.UserPreferencesResponse;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserPreferencesThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Controller implementation for managing user preferences.
 * Provides endpoints to save, retrieve, and delete user preference themes,
 * as well as uploading and deleting profile pictures.
 */
@RestController
@RequiredArgsConstructor
public class UserPreferencesControllerImplementation implements UserPreferencesController {
    private final UserPreferencesThemeService userPreferencesThemeService;
    private final GlobalMessageSource globalMessageSource;

    /**
     * Saves user preferences theme.
     *
     * @param preferencesSchema The schema containing user preferences theme data.
     * @return ApiResponse with the saved user preferences data.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<UserPreferencesResponse> saveUserPreferencesTheme(UserPreferencesSchema preferencesSchema) throws JsonProcessingException {
        UserPreferencesResponse data = userPreferencesThemeService.saveUserPreferencesTheme(preferencesSchema);
        return new ApiResponse<>(data, true, globalMessageSource.get(USER_PREFERENCE_THEME_SAVED_SUCCESS));
    }

    /**
     * Retrieves user preferences theme data by user ID.
     *
     * @return ApiResponse with the user preferences schema.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public ApiResponse<UserPreferencesSchema> getUserPreferencesThemesDataByUserId() throws IOException {
        return new ApiResponse<>(userPreferencesThemeService.getUserPreferencesThemeByUserId(), true, globalMessageSource.get(GET_USER_PREFERENCE_THEME_SUCCESS));
    }

    /**
     * Deletes user preferences theme data by user ID.
     *
     * @return ApiResponse indicating success or failure.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<Void> deleteUserPreferencesThemeDataByUserId() throws JsonProcessingException {
        userPreferencesThemeService.deleteUserPreferencesThemeByUserId();
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_USER_PREFERENCE_THEME_SUCCESS));
    }

    /**
     * Uploads a profile picture for the user.
     *
     * @param profilePicture The profile picture file to upload.
     * @return ApiResponse containing the uploaded profile picture response.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public ApiResponse<ProfilePictureResponse> uploadProfilePictureByUserId(MultipartFile profilePicture) throws IOException {
        ProfilePictureResponse data = userPreferencesThemeService.uploadProfilePictureByUserId(profilePicture);
        return new ApiResponse<>(data, true, globalMessageSource.get(USER_PREFERENCE_PROFILE_PHOTO_SAVED_SUCCESS));
    }

    /**
     * Deletes the user's profile picture.
     *
     * @return ApiResponse indicating success or failure.
     * @throws JsonProcessingException if JSON processing fails.
     */
    @Override
    public ApiResponse<Void> deleteProfilePhotoByUserId() throws JsonProcessingException {
        userPreferencesThemeService.deleteProfilePictureByUserId();
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_USER_PROFILE_PICTURE_SUCCESS));
    }
}
