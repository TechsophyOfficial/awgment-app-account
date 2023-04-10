package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ProfilePictureResponse;
import com.techsophy.tsf.account.dto.UserPreferencesResponse;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(BASE_URL + VERSION_V1 + USER_PREFERENCES_URL)
public interface UserPreferencesController
{
    @PostMapping
    ApiResponse<UserPreferencesResponse> saveUserPreferencesTheme(@RequestBody @Validated UserPreferencesSchema themesSchema) throws JsonProcessingException;


    @GetMapping
    ApiResponse<UserPreferencesSchema>  getUserPreferencesThemesDataByUserId() throws IOException;

    @DeleteMapping
    @PreAuthorize(DELETE_OR_ALL_ACCESS)
    ApiResponse<Void> deleteUserPreferencesThemeDataByUserId() throws JsonProcessingException;

    @PostMapping(value =PROFILE_PICTURE_URL,consumes={MediaType.MULTIPART_FORM_DATA_VALUE })
    ApiResponse<ProfilePictureResponse> uploadProfilePictureByUserId(@RequestParam(FILE) MultipartFile file) throws IOException;

    @DeleteMapping(PROFILE_PICTURE_URL)
    ApiResponse<Void> deleteProfilePhotoByUserId() throws JsonProcessingException;
}
