package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.ACLController;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.CheckACLSchema;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.ACLService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.AccessDeniedException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of the ACLController interface that handles
 * API requests related to Access Control List (ACL) operations.
 */
@RestController
@RequiredArgsConstructor
public class ACLControllerImpl implements ACLController
{
    private final GlobalMessageSource globalMessageSource;
    private final ACLService aclService;

    /**
     * Saves an ACL entry.
     *
     * @param aclSchema The ACL schema object containing the details to be saved.
     * @return ApiResponse containing the saved ACL schema and success message.
     * @throws JsonProcessingException If there is an issue processing JSON data.
     */
    @Override
    public ApiResponse<ACLSchema> saveACL(ACLSchema aclSchema) throws JsonProcessingException
    {
        return new ApiResponse<>(aclService.saveACL(aclSchema), true, globalMessageSource.get(ACL_SAVED_SUCCESSFULLY));
    }

    /**
     * Retrieves all ACL entries with pagination.
     *
     * @param page     The page number to retrieve.
     * @param pageSize The number of entries per page.
     * @return ApiResponse containing a paginated list of ACL definitions.
     */
    @Override
    public ApiResponse<Page<ACLDefinition>> getAllACLs(Integer page, Integer pageSize)
    {
        return new ApiResponse<>(aclService.getAllACLs(PageRequest.of(page, pageSize)), true, globalMessageSource.get(ACL_RETRIEVE_SUCCESS));
    }

    /**
     * Retrieves an ACL entry by its ID.
     *
     * @param id The unique identifier of the ACL entry.
     * @return ApiResponse containing the requested ACL schema.
     */
    @Override
    public ApiResponse<ACLSchema> getACLById(String id)
    {
        return new ApiResponse<>(aclService.getACLById(id), true, globalMessageSource.get(ACL_RETRIEVE_SUCCESS));
    }

    /**
     * Checks if access is granted based on the given ACL rules.
     *
     * @param id               The unique identifier of the ACL entry.
     * @param checkACLSchema   The schema containing details to validate ACL access.
     * @return ApiResponse containing the validation result.
     * @throws JsonProcessingException If there is an issue processing JSON data.
     * @throws AccessDeniedException   If access is denied.
     */
    @Override
    public ApiResponse<ACLValidate> checkACLAccess(String id, CheckACLSchema checkACLSchema) throws JsonProcessingException, AccessDeniedException
    {
        return new ApiResponse<>(aclService.checkACLAccess(id, checkACLSchema), true, globalMessageSource.get(ACL_EVALUATION_SUCCESS));
    }
}
