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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.PropertyConstant.X_SIGNATURE;

/**
 * Implementation of {@link InternalController} that handles internal user data processing.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class InternalControllerImpl implements InternalController {
    private final UserFormDataService userFormDataService;
    private final GlobalMessageSource globalMessageSource;

    @Value(ENCRYPTION_KEY_FILE)
    String keycloakPublicFile;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    Rsa4096 rsa4096;

    /**
     * Initializes the RSA encryption utility with the public key file.
     */
    @PostConstruct
    void initializeRsa() {
        rsa4096 = new Rsa4096(keycloakPublicFile);
    }

    /**
     * Saves a user after verifying the request signature.
     *
     * @param internalUserFormDataSchema The user form data to be saved.
     * @param headers                    The HTTP headers containing the request signature.
     * @return ApiResponse containing the saved user form data.
     * @throws JsonProcessingException If an error occurs while processing JSON.
     */
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
