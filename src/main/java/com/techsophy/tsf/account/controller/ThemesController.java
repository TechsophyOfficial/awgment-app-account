package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ThemesResponseSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.dto.ThemesResponse;
import com.techsophy.tsf.account.dto.ThemesSchema;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(BASE_URL+VERSION_V1+THEMES_URL)
public interface ThemesController
{
    @PostMapping
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<ThemesResponse> saveThemesData(@RequestBody @Validated ThemesSchema themesSchema) throws JsonProcessingException;

    @GetMapping(BY_ID_URL)
    ApiResponse<ThemesResponseSchema>  getThemesDataById(@PathVariable(ID) String id);

    @GetMapping
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<Stream<ThemesResponseSchema>> getAllThemesData(@RequestParam(value=DEPLOYMENT,required = false) String deploymentIdList,
                                                       @RequestParam(value = QUERY,required = false) String q,
                                                       @RequestParam(value = PAGE, required = false) Integer page,
                                                       @RequestParam(value =SIZE, required = false) Integer pageSize,
                                                       @RequestParam(value = SORT_BY, required = false) String[] sortBy);

    @DeleteMapping(BY_ID_URL)
    @PreAuthorize(DELETE_OR_ALL_ACCESS)
    ApiResponse<Void> deleteThemesDataById(@PathVariable(ID) String id);

    @GetMapping(EXPORT_BY_ID_URL)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ResponseEntity<Resource> downloadTheme(@PathVariable(ID) String id) throws IOException;

    @PostMapping(value=IMPORT_URL,consumes={MediaType.MULTIPART_FORM_DATA_VALUE })
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<ThemesResponse> uploadTheme(@RequestPart(value= FILE) MultipartFile file,@RequestParam(value=NAME)String name) throws IOException, ClassNotFoundException;
}
