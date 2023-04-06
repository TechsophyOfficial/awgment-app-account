package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.InternalUserFormDataController;
import com.techsophy.tsf.account.dto.InternalUserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.InternalUserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;

import static com.techsophy.tsf.account.constants.AccountConstants.SAVE_FORM_SUCCESS;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class InternalUserFormDataControllerImpl implements InternalUserFormDataController {
    private final InternalUserFormDataService internalUserFormDataService;
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;
    private final ObjectMapper objectMapper;
    @Override
    public ApiResponse<InternalUserFormDataSchema> saveUser(String signature, InternalUserFormDataSchema userFormDataSchema, HttpHeaders headers) throws JsonProcessingException {
        return new ApiResponse<>(internalUserFormDataService.saveUserFormData(signature, userFormDataSchema),true,globalMessageSource.get(SAVE_FORM_SUCCESS));
    }
}
