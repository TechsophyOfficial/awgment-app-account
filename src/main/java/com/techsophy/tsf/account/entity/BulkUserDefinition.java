package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.TP_USER_BULK_UPLOAD;
import static com.techsophy.tsf.account.constants.AccountConstants.USER_ID_NOT_NULL;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection =TP_USER_BULK_UPLOAD)
public class BulkUserDefinition extends Auditable
{
    @NotNull(message = USER_ID_NOT_NULL)
    @Id
    private BigInteger id;
    private Map<String,Object> userData;
    private BigInteger documentId;
    private String status;
}
