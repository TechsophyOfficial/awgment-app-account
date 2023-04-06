package com.techsophy.tsf.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotEmpty;
import java.util.Map;

import static com.techsophy.tsf.account.constants.AccountConstants.USER_DATA_NOT_EMPTY;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class InternalUserFormDataSchema extends AuditableData
{
    @NotEmpty(message = USER_DATA_NOT_EMPTY)
    Map<String, Object> userData;
    String realmId;
    String version;
}
