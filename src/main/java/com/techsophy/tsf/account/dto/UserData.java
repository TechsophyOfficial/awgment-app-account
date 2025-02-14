package com.techsophy.tsf.account.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@AllArgsConstructor
@NoArgsConstructor
public class UserData extends AuditableData
{
    String id;
    @NotNull(message = USER_NAME_NOT_NULL) String userName;
    @NotBlank(message = USER_FIRST_NAME_NOT_BLANK) String firstName;
    @NotBlank(message = USER_LAST_NAME_NOT_BLANK) String lastName;
    @NotBlank(message = MOBILE_NUMBER_NOT_BLANK) String mobileNumber;
    @NotBlank(message = EMAIL_ID_NOT_BLANK) String emailId;
    @NotBlank(message = DEPARTMENT_NAME_NOT_BLANK) String department;
}
