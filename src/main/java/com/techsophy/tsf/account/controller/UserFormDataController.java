package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.security.auth.login.AccountNotFoundException;
import java.io.IOException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping(ACCOUNTS_URL+VERSION_V1+USERS_URL)
public interface UserFormDataController
{
    @GetMapping("/loggedIn")
    ApiResponse<AuditableData> getUserDetailsOfLoggedInUser() throws JsonProcessingException;

    @PostMapping("/loggedIn")
    ApiResponse<UserFormDataSchema> updateUserDetailsOfLoggedInUser(@RequestBody @Validated UserFormDataSchema userFormDataSchema);

    @PostMapping
    @PreAuthorize(CREATE_OR_ALL_ACCESS)
    ApiResponse<UserFormDataSchema> saveUser(@RequestBody @Validated UserFormDataSchema userFormDataSchema, @RequestHeader HttpHeaders headers) throws JsonProcessingException;

    @GetMapping(USER_ID_URL)
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<AuditableData> getUserByUserId(@PathVariable(USER_ID) String userId,@RequestParam(value = ONLY_MANDATORY_FIELDS, required = false) Boolean onlyMandatoryFields) throws IOException, AccountNotFoundException;

    @GetMapping
    @PreAuthorize(READ_OR_ALL_ACCESS)
    ApiResponse<Void> getAllUsers(@RequestParam(value = QUERY,required = false) String q, @RequestParam(value = ONLY_MANDATORY_FIELDS, required = false) Boolean onlyMandatoryFields,
                            @RequestParam(value = PAGE, required = false) Integer page,
                            @RequestParam(value = SIZE, required = false) Integer pageSize,
                            @RequestParam(value = SORT_BY, required = false) String[] sortBy,
                            @RequestParam(value = FILTER_COLUMN_NAME, required = false) String filterColumn,
                            @RequestParam(value = FILTER_VALUE, required = false) String filterValue);

    @DeleteMapping(USER_ID_URL)
    @PreAuthorize(DELETE_OR_ALL_ACCESS)
    ApiResponse<Void> deleteUserByUserId(@PathVariable(USER_ID) String userId);
}
