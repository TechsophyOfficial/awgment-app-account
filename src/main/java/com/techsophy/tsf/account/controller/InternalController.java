package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.InternalUserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(INTERNAL+VERSION_V1+USER_CREATE)
public interface InternalController {
    @PostMapping
    ApiResponse<InternalUserFormDataSchema> saveUser(@RequestParam("signature") String signature, @RequestBody @Validated InternalUserFormDataSchema userFormDataSchema, @RequestHeader HttpHeaders headers) throws JsonProcessingException;
}
