package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TP_FORM_DATA_USER_COLLECTION)
public class UserFormDataDefinition extends Auditable
{
    private static final long serialVersionUID = 1L;
    @NotNull(message = ID_NOT_NULL)
    @Id
    private BigInteger id;
    @NotEmpty(message = USER_DATA_NOT_EMPTY)
    private  Map<String, Object> userData;
    @NotNull(message = USER_ID_NOT_NULL)
    private BigInteger userId;
    @NotNull(message = VERSION_NOT_NULL)
    private Integer version;
}
