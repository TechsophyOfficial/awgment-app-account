package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.model.ApiResponse;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.nio.file.AccessDeniedException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(BASE_URL+VERSION_V1+ACL)
public interface ACLController
{
    @PostMapping(CREATE_URL)
    @PreAuthorize(CREATE_ACL_ACCESS)
    @ApiOperation(value =SAVE_ACL ,notes=REQUIRES_ROLE+AWGMENT_ACL_CREATE_OR_UPDATE)
    ApiResponse<ACLSchema> saveACL(@RequestBody @Validated ACLSchema aclSchema) throws JsonProcessingException;

    @GetMapping
    @PreAuthorize(READ_ACL_ACCESS)
    @ApiOperation(value = GET_ALL_ACLS,notes=REQUIRES_ROLE+AWGMENT_ACL_READ)
    ApiResponse<PaginationResponsePayload> getAllACLs(@RequestParam(defaultValue ="0",value=PAGE) Integer page,@RequestParam(defaultValue ="200",value = SIZE) Integer pageSize);

    @GetMapping(ID_URL)
    @PreAuthorize(READ_ACL_ACCESS)
    @ApiOperation(value = GET_ACL_BY_ID,notes=REQUIRES_ROLE+AWGMENT_ACL_READ)
    ApiResponse<ACLSchema> getACLById(@PathVariable(ID) String id);

    @PostMapping(CHECK_ACCESS_URL)
    @ApiOperation(value = CHECKS_ACL,notes=DOES_NOT_REQUIRES_ROLE)
    ApiResponse<ACLValidate>  checkACLAccess(@PathVariable(ID) String id) throws JsonProcessingException, AccessDeniedException;
}
