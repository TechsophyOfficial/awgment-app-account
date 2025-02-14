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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.FILTER_OR_Q_REQUIRED;

/**
 * Implementation of the {@link BulkUserController} that handles bulk user operations.
 */
@RestController
@RequiredArgsConstructor
public class BulkUserControllerImpl implements BulkUserController
{
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final BulkUserService bulkUserService;

    /**
     * Handles bulk user upload from a file.
     *
     * @param file The multipart file containing user data.
     * @return ApiResponse containing bulk upload response.
     * @throws IOException If an error occurs during file processing.
     */
    @Override
    public ApiResponse<BulkUploadResponse> bulkUploadUsers(MultipartFile file) throws IOException
    {
        BulkUploadResponse bulkUploadResponse = bulkUserService.bulkUploadUsers(file);
        return new ApiResponse<>(bulkUploadResponse, true, globalMessageSource.get(BULK_UPLOAD_SUCCESS));
    }

    /**
     * Updates the status of bulk uploaded users.
     *
     * @param bulkSchema The schema containing status update details.
     * @return ApiResponse containing bulk upload response.
     * @throws JsonProcessingException If an error occurs during JSON processing.
     */
    @Override
    public ApiResponse<BulkUploadResponse> bulkUpdateStatus(BulkUploadSchema bulkSchema) throws JsonProcessingException
    {
        BulkUploadResponse bulkUploadResponse = bulkUserService.bulkUpdateStatus(bulkSchema);
        return new ApiResponse<>(bulkUploadResponse, true, globalMessageSource.get(BULK_STATUS_UPDATE_SUCCESS));
    }

    /**
     * Retrieves all bulk users based on filters or search query.
     *
     * @param q            The search query string.
     * @param page         The page number for pagination.
     * @param pageSize     The size of each page.
     * @param sortBy       The field to sort by.
     * @param sortOrder    The sorting order (ASC/DESC).
     * @param filterColumn The column name to filter by.
     * @param filterValue  The filter value.
     * @return ApiResponse containing the list of bulk users.
     * @throws InvalidInputException If both filter and query parameters are missing.
     */
    @Override
    public ApiResponse getAllBulkUsers(String q, Integer page, Integer pageSize, String sortBy, String sortOrder, String filterColumn, String filterValue)
    {
        if (StringUtils.hasText(filterColumn) && StringUtils.hasText(filterValue))
        {
            if (page == null)
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(filterColumn, filterValue, sortBy, sortOrder), true, globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
            else
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(filterColumn, filterValue, sortBy, sortOrder, PageRequest.of(page, pageSize)), true, globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
        }
        else if (StringUtils.hasText(q))
        {
            if (page == null)
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(q, sortBy, sortOrder), true, globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
            else
            {
                return new ApiResponse<>(bulkUserService.getAllBulkUsers(q, sortBy, sortOrder, PageRequest.of(page, pageSize)), true, globalMessageSource.get(GET_ALL_USERS_SUCCESS));
            }
        }
        throw new InvalidInputException(FILTER_OR_Q_REQUIRED, globalMessageSource.get(FILTER_OR_Q_REQUIRED));
    }

    /**
     * Deletes a bulk user by ID.
     *
     * @param id The unique identifier of the bulk user to be deleted.
     * @return ApiResponse confirming deletion success.
     */
    @Override
    public ApiResponse<Void> deleteBulkUserById(String id)
    {
        bulkUserService.deleteBulkUserById(id);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_USER_SUCCESS));
    }
}
