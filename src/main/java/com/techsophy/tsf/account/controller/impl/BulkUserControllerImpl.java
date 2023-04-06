package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.BulkUserController;
import com.techsophy.tsf.account.dto.BulkUploadResponse;
import com.techsophy.tsf.account.dto.BulkUploadSchema;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.BulkUserService;
import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.FILTER_OR_Q_REQUIRED;

@RestController
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class BulkUserControllerImpl implements BulkUserController
{
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final BulkUserService bulkUserService;

    @Override
    public ApiResponse<BulkUploadResponse> bulkUploadUsers(MultipartFile file) throws IOException
    {
        BulkUploadResponse bulkUploadResponse=bulkUserService.bulkUploadUsers(file);
        return new ApiResponse<>(bulkUploadResponse,true,globalMessageSource.get(BULK_UPLOAD_SUCCESS));
    }

    @Override
    public ApiResponse<BulkUploadResponse> bulkUpdateStatus(BulkUploadSchema bulkSchema) throws JsonProcessingException
    {
        BulkUploadResponse bulkUploadResponse=bulkUserService.bulkUpdateStatus(bulkSchema);
        return new ApiResponse<>(bulkUploadResponse,true,globalMessageSource.get(BULK_STATUS_UPDATE_SUCCESS));
    }

    @Override
    public ApiResponse getAllBulkUsers(String q, Integer page, Integer pageSize, String sortBy,String sortOrder, String filterColumn, String filterValue)
    {
        if (StringUtils.hasText(filterColumn) && StringUtils.hasText(filterValue))
        {
            if(page==null)
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(filterColumn,filterValue,sortBy,sortOrder),true, globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
            else
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(filterColumn,filterValue,sortBy, sortOrder,PageRequest.of(page,pageSize)),true,globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
        }
        else if(StringUtils.hasText(q))
        {
            if(page==null)
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(q,sortBy,sortOrder),true,globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
            else
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(q,sortBy,sortOrder,PageRequest.of(page,pageSize)),true,globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
        }
        throw new InvalidInputException(FILTER_OR_Q_REQUIRED,globalMessageSource.get(FILTER_OR_Q_REQUIRED));
    }

    @Override
    public ApiResponse<Void> deleteBulkUserById(String id)
    {
        bulkUserService.deleteBulkUserById(id);
        return new ApiResponse<>(null,true,globalMessageSource.get(DELETE_USER_SUCCESS));
    }
}
