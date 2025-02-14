package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TP_USER_COLLECTION )
public class UserDefinition extends Auditable
{
    private static final long serialVersionUID = 1L;
    @NotNull(message = USER_ID_NOT_NULL)
    @Id
    private BigInteger id;
    @NotBlank(message = USER_NAME_NOT_NULL)
    private String userName;
    @NotBlank(message = USER_FIRST_NAME_NOT_BLANK)
    private String firstName;
    @NotBlank(message = USER_LAST_NAME_NOT_BLANK)
    private String lastName;
    @NotBlank(message = MOBILE_NUMBER_NOT_BLANK)
    private String mobileNumber;
    @NotBlank(message = EMAIL_ID_NOT_BLANK)
    private String emailId;
    @NotBlank(message = DEPARTMENT_NAME_NOT_BLANK)
    private String department;
}
