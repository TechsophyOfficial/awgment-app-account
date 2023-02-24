package com.techsophy.tsf.account.dto;

import lombok.*;

import javax.validation.constraints.NotEmpty;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.USER_DATA_NOT_EMPTY;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class UserFormDataSchema extends AuditableData
{
    @NotEmpty(message = USER_DATA_NOT_EMPTY)
    Map<String, Object> userData;
    String userId;
    String version;
}
