package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.ThemesConstants;
import com.techsophy.tsf.account.controller.impl.ThemesControllerImplementation;
import com.techsophy.tsf.account.dto.ThemesResponse;
import com.techsophy.tsf.account.dto.ThemesResponseSchema;
import com.techsophy.tsf.account.dto.ThemesSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.ThemesService;
import com.techsophy.tsf.account.utils.TokenUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.techsophy.tsf.account.constants.ThemesConstants.*;
import static com.techsophy.tsf.account.constants.UserConstants.ID;
import static com.techsophy.tsf.account.constants.UserConstants.NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThemesControllerTest
{
    @InjectMocks
    ThemesControllerImplementation themesControllerImplementation;
    @Mock
    ThemesService themesService;
    @Mock
    TokenUtils tokenUtils;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    InputStreamResource inputStreamResource;
    @Mock
    MultipartFile file1;

    @Order(1)
    @Test
    void saveThemesDataTest() throws JsonProcessingException
    {
        ThemesSchema themesSchema=new ThemesSchema(ID,NAME,CONTENT);
        Mockito.when(themesService.saveThemesData(themesSchema))
                .thenReturn(new ThemesResponse(ID));
        ApiResponse<ThemesResponse> responseEntity = themesControllerImplementation.saveThemesData(themesSchema);
        assertEquals(true,responseEntity.getSuccess());
        verify(themesService, times(1)).saveThemesData(themesSchema);
    }

    @Test
    void getThemesDataByIdTest()
    {
        Mockito.when(themesService.getThemesDataById(ID)).thenReturn(new ThemesResponseSchema(ID,NAME,CONTENT,CREATED_BY_ID,CREATED_ON,UPDATED_BY_ID,UPDATED_ON));
        ApiResponse<ThemesResponseSchema> responseEntity=themesControllerImplementation.getThemesDataById(ID);
        assertEquals(true,responseEntity.getSuccess());
        verify(themesService, times(2)).getThemesDataById(ID);
    }

    @Test
    void getAllThemesListTest()
    {
        ApiResponse<ThemesResponseSchema> responseEntity=themesControllerImplementation.getAllThemesData("abc","theme1", null,null,new String[0]);
        assertEquals(true,responseEntity.getSuccess());
    }


    @Test
    void getAllThemesPaginationTest()
    {
        ApiResponse<ThemesResponse> responseEntity=themesControllerImplementation.getAllThemesData("abc","theme1", 0,5,new String[0]);
        assertEquals(true,responseEntity.getSuccess());
    }

    @Test
    void deleteThemesDataByIdTest()
    {
        ApiResponse<Void> responseEntity=themesControllerImplementation.deleteThemesDataById(ID);
        assertEquals(true,responseEntity.getSuccess());
        themesService.deleteThemesDataById(ID);
    }

    @Test
    void downloadThemeByIdTest() throws IOException
    {
        String fileName = "file1";
        Mockito.when(themesService.downloadTheme(ThemesConstants.ID)).thenReturn(ResponseEntity.ok().header("Content-Type", "application/json")
                .header("Content-Disposition", "attachment; filename=\"" + fileName +".json" + "\"")
                .body(inputStreamResource));
        ResponseEntity<Resource> responseEntity=themesControllerImplementation.downloadTheme(ThemesConstants.ID);
        assertNotNull(responseEntity);
        verify(themesService, times(1)).downloadTheme(ThemesConstants.ID);
    }

    @Test
    void uploadThemeTest() throws IOException
    {
        String themeName = "name";
        ApiResponse responseEntity=themesControllerImplementation.uploadTheme(file1, themeName);
        assertEquals(true,responseEntity.getSuccess());
        verify(themesService, times(1)).uploadTheme(file1, themeName);
    }
}
