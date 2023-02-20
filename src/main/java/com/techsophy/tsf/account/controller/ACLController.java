package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.model.ApiResponse;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.nio.file.AccessDeniedException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping("/accounts/v1/acl")
public interface ACLController
{
    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('awgment-acl-create-or-update')")
    @ApiOperation(value ="Save Access Control" ,notes="Requires role awgment-acl-create-or-update")
    ApiResponse<ACLSchema> saveACL(@RequestBody @Validated ACLSchema aclSchema) throws JsonProcessingException;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('awgment-acl-read')")
    @ApiOperation(value = GET_ALL_ACLS,notes="Requires role awgment-acl-read")
    ApiResponse<Page<ACLDefinition>> getAllACLs(@RequestParam(defaultValue ="0",value=PAGE) Integer page,
                                                @RequestParam(defaultValue ="200",value = SIZE) Integer pageSize);

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('awgment-acl-read')")
    @ApiOperation(value = GET_ACL_BY_ID,notes="Requires role awgment-acl-read")
    ApiResponse<ACLSchema> getACLById(@PathVariable("id") String id);

    @PostMapping("/{id}/evaluate")
    @ApiOperation(value = CHECKS_ACL,notes="Does not require any role")
    ApiResponse<ACLValidate>  checkACLAccess(@PathVariable("id") String id) throws JsonProcessingException, AccessDeniedException;
}
