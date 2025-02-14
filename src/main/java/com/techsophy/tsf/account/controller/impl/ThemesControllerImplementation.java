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
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@RequiredArgsConstructor
public class ThemesControllerImplementation implements ThemesController
{
    private final GlobalMessageSource globalMessageSource;
    private final ThemesService themesService;
    private final TokenUtils accountUtils;

    @Override
    public ApiResponse<ThemesResponse> saveThemesData(ThemesSchema themesSchema) throws JsonProcessingException
    {
        ThemesResponse data = themesService.saveThemesData(themesSchema);
        return new ApiResponse<>(data,true, globalMessageSource.get(SAVE_THEME_SUCCESS));
    }

    @Override
    public ApiResponse<ThemesResponseSchema> getThemesDataById(String id)
    {
        themesService.getThemesDataById(id);
        return new ApiResponse<>(themesService.getThemesDataById(id),true, globalMessageSource.get(GET_THEME_SUCCESS));
    }

    @Override
    public ApiResponse getAllThemesData(String deploymentList, String q, Integer page, Integer pageSize, String[] sortBy)
    {
        if (page == null)
        {
            return new ApiResponse<>(themesService.getAllThemesData(deploymentList,q,accountUtils.getSortBy(sortBy)), true, globalMessageSource.get(GET_ALL_THEMES_SUCCESS));
        }
        return new ApiResponse<>(themesService.getAllThemesData(q,accountUtils.getPageRequest(page,pageSize,sortBy)), true, globalMessageSource.get(GET_ALL_THEMES_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteThemesDataById(String id)
    {
        themesService.deleteThemesDataById(id);
        return new ApiResponse<>(null,true, globalMessageSource.get(DELETE_THEME_SUCCESS));
     }

    @Override
    public ResponseEntity<Resource> downloadTheme(String id) throws IOException
    {
        return themesService.downloadTheme(id);
    }

    @Override
    public ApiResponse<ThemesResponse> uploadTheme(@RequestPart(value= FILE) MultipartFile file, String name) throws IOException
    {
        return new ApiResponse<>(themesService.uploadTheme(file,name),true, globalMessageSource.get(UPLOAD_THEME_SUCCESS));
    }
}
