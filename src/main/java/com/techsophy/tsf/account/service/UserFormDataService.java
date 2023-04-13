package com.techsophy.tsf.account.service;

import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserDataSchema;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface UserFormDataService
{
    @Transactional(rollbackFor = Exception.class)
    UserFormDataSchema saveUserFormData(UserFormDataSchema userFormDataSchema);



    UserFormDataDefinition getUserFormData(String userName);

    AuditableData getUserFormDataByUserId(String userId, Boolean onlyMandatoryFields);

    List<UserDataSchema> getAllUserFormDataObjects(Boolean onlyMandatoryFields,String q, Sort sort);

    PaginationResponsePayload getAllUserFormDataObjects(Boolean onlyMandatoryFields,String q,Pageable pageable);

    List<AuditableData> getAllUsersByFilter(Boolean onlyMandatoryFields, String filterColumn, String filterValue, Sort sort, String q);

    PaginationResponsePayload getAllUsersByFilter(Boolean onlyMandatoryFields,String filterColumn,String filterValue,Pageable pageable,String q);

    @Transactional(rollbackFor = Exception.class)
    void deleteUserFormDataByUserId(String userId);



}
