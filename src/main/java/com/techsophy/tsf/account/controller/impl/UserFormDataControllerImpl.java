package com.techsophy.tsf.account.controller.impl;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.UserFormDataController;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserFormDataControllerImpl implements UserFormDataController
{
    private final UserFormDataService userFormDataService;
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;

    @Override
    public ApiResponse<AuditableData> getUserDetailsOfLoggedInUser() {
        try {
            String userId = (String) userDetails.getUserDetails().get(0).get(ID);
            return new ApiResponse<>(userFormDataService.getUserFormDataByUserId(userId, false), true, "Logged In User details fetched successfully");
        }
        catch (Exception e)
        {
            throw new RunTimeException(e.getMessage());
        }
    }
    @Override
    public ApiResponse<UserFormDataSchema> updateUserDetailsOfLoggedInUser(UserFormDataSchema userFormDataSchema) {
        return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema),true,"updated Successfully");
    }
    @Override
    public ApiResponse<UserFormDataSchema> saveUser(UserFormDataSchema userFormDataSchema,HttpHeaders headers)
    {
        return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema),true,globalMessageSource.get(SAVE_FORM_SUCCESS));
    }

    @Override
    public ApiResponse<AuditableData> getUserByUserId(String userId, Boolean onlyMandatoryFields)
    {
        return new ApiResponse<>(userFormDataService.getUserFormDataByUserId(userId,onlyMandatoryFields), true, globalMessageSource.get(GET_FORM_SUCCESS));
     }

    @Override
    public ApiResponse getAllUsers(String q,Boolean onlyMandatoryFields,Integer page, Integer pageSize, String[] sortBy, String filterColumn, String filterValue)
    {
        if (StringUtils.hasText(filterColumn) && StringUtils.hasText(filterValue))
        {
            if(page==null)
            {
                return new ApiResponse<>(userFormDataService.getAllUsersByFilter(onlyMandatoryFields,filterColumn,filterValue,tokenUtils.getSortBy(sortBy),q), true,
                        globalMessageSource.get(GET_FORM_SUCCESS));
            }
            else
            {
                return new ApiResponse<>(userFormDataService.getAllUsersByFilter(onlyMandatoryFields, filterColumn, filterValue, tokenUtils.getPageRequest(page, pageSize, sortBy), q), true,
                        globalMessageSource.get(GET_FORM_SUCCESS));
            }
        }
        if (page == null)
        {
            return new ApiResponse<>(userFormDataService.getAllUserFormDataObjects(onlyMandatoryFields,q, tokenUtils.getSortBy(sortBy)), true,
                        globalMessageSource.get(GET_FORM_SUCCESS));
        }
          return new ApiResponse<>(userFormDataService.getAllUserFormDataObjects(onlyMandatoryFields,q, tokenUtils.getPageRequest(page,pageSize,sortBy)), true,
                        globalMessageSource.get(GET_FORM_SUCCESS));
    }

    @Override
    public ApiResponse<Void> deleteUserByUserId(String userId)
    {
        userFormDataService.deleteUserFormDataByUserId(userId);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_FORM_SUCCESS));
    }
}
