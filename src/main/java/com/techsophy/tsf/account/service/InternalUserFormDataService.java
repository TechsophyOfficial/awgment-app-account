package com.techsophy.tsf.account.service;

import com.techsophy.tsf.account.dto.InternalUserFormDataSchema;
import org.springframework.transaction.annotation.Transactional;

public interface InternalUserFormDataService
{
    @Transactional(rollbackFor = Exception.class)
    InternalUserFormDataSchema saveUserFormData(String signature, InternalUserFormDataSchema userFormDataSchema);
}
