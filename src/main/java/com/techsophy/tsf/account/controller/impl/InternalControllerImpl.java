package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.InternalController;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.Rsa4096;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.ConstraintViolationException;

import static com.techsophy.tsf.account.constants.AccountConstants.SAVE_FORM_SUCCESS;

@RestController
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class InternalControllerImpl implements InternalController {
    private final UserFormDataService userFormDataService;
    private final GlobalMessageSource globalMessageSource;
    @Override
    public ApiResponse<UserFormDataSchema> saveUser(String signature, UserFormDataSchema internalUserFormDataSchema, HttpHeaders headers) throws JsonProcessingException
    {
        try
        {
            Rsa4096 rsa4096 = new Rsa4096();
            UserFormDataSchema userFormDataSchema = rsa4096.transform(internalUserFormDataSchema);
            return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema),true,globalMessageSource.get(SAVE_FORM_SUCCESS));
        }
        catch (ConstraintViolationException | BadRequestException e)
        {
            throw e;
        }
        catch (Exception e) {
            throw new RunTimeException(e.getMessage());
        }
    }
}
