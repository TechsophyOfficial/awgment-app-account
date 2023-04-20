package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(INTERNAL+VERSION_V1)
public interface InternalController {
    @PostMapping(USER_CREATE)
    ApiResponse<UserFormDataSchema> saveUser( @RequestBody @Validated UserFormDataSchema userFormDataSchema, @RequestHeader HttpHeaders headers) throws JsonProcessingException;
}
