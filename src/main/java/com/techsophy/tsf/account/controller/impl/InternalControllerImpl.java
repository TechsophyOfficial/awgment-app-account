package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.InternalController;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.Rsa4096;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.PropertyConstant.X_SIGNATURE;

@RestController
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class InternalControllerImpl implements InternalController {
    private final UserFormDataService userFormDataService;
    private final GlobalMessageSource globalMessageSource;
    @Value(ENCRYPTION_KEY_FILE)
    String keycloakPublicFile;
    Rsa4096 rsa4096;
    @PostConstruct
    void initializeRsa() {
        rsa4096 = new Rsa4096(keycloakPublicFile);
    }
    @Override
    public ApiResponse<UserFormDataSchema> saveUser(UserFormDataSchema internalUserFormDataSchema, HttpHeaders headers) throws JsonProcessingException
    {
        try
        {
            if(headers.containsKey(X_SIGNATURE)) {
                String headerSign = headers.getFirst(X_SIGNATURE);
                UserFormDataSchema userFormDataSchema = rsa4096.transform(headerSign, internalUserFormDataSchema);
                return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema), true, globalMessageSource.get(SAVE_FORM_SUCCESS));
            }
            else
            {
                return new ApiResponse<>(null, false, globalMessageSource.get(SIGNATURE_MISSING));
            }
        }
        catch (Exception e)
        {
            throw new RunTimeException(e.getMessage());
        }
    }
}
