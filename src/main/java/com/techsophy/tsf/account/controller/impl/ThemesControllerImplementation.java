package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.ThemesController;
import com.techsophy.tsf.account.dto.ThemesResponse;
import com.techsophy.tsf.account.dto.ThemesResponseSchema;
import com.techsophy.tsf.account.dto.ThemesSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.ThemesService;
import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of {@link ThemesController} responsible for handling theme-related operations.
 */
@RestController
@RequiredArgsConstructor
public class ThemesControllerImplementation implements ThemesController {
    private final GlobalMessageSource globalMessageSource;
    private final ThemesService themesService;
    private final TokenUtils accountUtils;

    /**
     * Saves theme data based on the provided {@link ThemesSchema}.
     *
     * @param themesSchema The schema containing theme details.
     * @return ApiResponse containing the saved theme data.
     * @throws JsonProcessingException If an error occurs during JSON processing.
     */
    @Override
    public ApiResponse<ThemesResponse> saveThemesData(ThemesSchema themesSchema) throws JsonProcessingException {
        ThemesResponse data = themesService.saveThemesData(themesSchema);
        return new ApiResponse<>(data, true, globalMessageSource.get(SAVE_THEME_SUCCESS));
    }

    /**
     * Retrieves theme data based on the provided theme ID.
     *
     * @param id The ID of the theme.
     * @return ApiResponse containing the theme data.
     */
    @Override
    public ApiResponse<ThemesResponseSchema> getThemesDataById(String id) {
        return new ApiResponse<>(themesService.getThemesDataById(id), true, globalMessageSource.get(GET_THEME_SUCCESS));
    }

    /**
     * Retrieves all themes with optional filtering, pagination, and sorting.
     *
     * @param deploymentList The deployment list filter.
     * @param q              The search query.
     * @param page           The page number for pagination.
     * @param pageSize       The number of records per page.
     * @param sortBy         The sorting criteria.
     * @return ApiResponse containing the list of themes.
     */
    @Override
    public ApiResponse getAllThemesData(String deploymentList, String q, Integer page, Integer pageSize, String[] sortBy) {
        if (page == null) {
            return new ApiResponse<>(themesService.getAllThemesData(deploymentList, q, accountUtils.getSortBy(sortBy)),
                    true, globalMessageSource.get(GET_ALL_THEMES_SUCCESS));
        }
        return new ApiResponse<>(themesService.getAllThemesData(q, accountUtils.getPageRequest(page, pageSize, sortBy)),
                true, globalMessageSource.get(GET_ALL_THEMES_SUCCESS));
    }

    /**
     * Deletes a theme based on the provided theme ID.
     *
     * @param id The ID of the theme to be deleted.
     * @return ApiResponse indicating the success of the deletion.
     */
    @Override
    public ApiResponse<Void> deleteThemesDataById(String id) {
        themesService.deleteThemesDataById(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_THEME_SUCCESS));
    }

    /**
     * Downloads a theme file based on the provided theme ID.
     *
     * @param id The ID of the theme to be downloaded.
     * @return ResponseEntity containing the theme file as a resource.
     * @throws IOException If an error occurs during file retrieval.
     */
    @Override
    public ResponseEntity<Resource> downloadTheme(String id) throws IOException {
        return themesService.downloadTheme(id);
    }

    /**
     * Uploads a new theme file with a specified name.
     *
     * @param file The theme file to be uploaded.
     * @param name The name of the theme.
     * @return ApiResponse containing the uploaded theme details.
     * @throws IOException If an error occurs during file upload.
     */
    @Override
    public ApiResponse<ThemesResponse> uploadTheme(@RequestPart(value = FILE) MultipartFile file, String name) throws IOException {
        return new ApiResponse<>(themesService.uploadTheme(file, name), true, globalMessageSource.get(UPLOAD_THEME_SUCCESS));
    }
}
