package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.InternalController;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.exception.UnAuthorizedException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.impl.UserFormDataServiceImpl;
import com.techsophy.tsf.account.utils.Rsa4096;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.ConstraintViolationException;

import static com.techsophy.tsf.account.constants.AccountConstants.SAVE_FORM_SUCCESS;
import static com.techsophy.tsf.account.constants.ErrorConstants.UN_AUTHORIZED_EXCEPTION;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class InternalControllerImpl implements InternalController {
    private final UserFormDataServiceImpl userFormDataServiceImpl;
    private final GlobalMessageSource globalMessageSource;
    private final ObjectMapper objectMapper;
    @Override
    public ApiResponse<UserFormDataSchema> saveUser(String signature, UserFormDataSchema internalUserFormDataSchema, HttpHeaders headers) throws JsonProcessingException
    {
        try
        {

            Rsa4096 rsa4096 = new Rsa4096();
            String sign = rsa4096.decryptFromBase64((String) internalUserFormDataSchema.getUserData().get("signature"));
            String signatureValue = internalUserFormDataSchema.getUserData().get("realmId")+(String)internalUserFormDataSchema.getUserData().get("userName");
            if(!sign.equals(signatureValue))
            {
                throw new UnAuthorizedException(UN_AUTHORIZED_EXCEPTION,globalMessageSource.get(UN_AUTHORIZED_EXCEPTION));
            }
            internalUserFormDataSchema.getUserData().remove("realmId");
            internalUserFormDataSchema.getUserData().remove("signature");
            UserFormDataSchema userFormDataSchema = this.objectMapper.convertValue(internalUserFormDataSchema,UserFormDataSchema.class);
            return new ApiResponse<>(userFormDataServiceImpl.saveUserFormData(userFormDataSchema),true,globalMessageSource.get(SAVE_FORM_SUCCESS));
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
