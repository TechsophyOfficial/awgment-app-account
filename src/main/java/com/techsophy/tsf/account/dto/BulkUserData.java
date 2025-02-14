package com.techsophy.tsf.account.dto;

import com.techsophy.tsf.account.entity.Auditable;
import lombok.*;
import org.springframework.data.annotation.Id;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.USER_ID_NOT_NULL;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
public class BulkUserData extends Auditable implements Serializable
{
    private static final long serialVersionUID = 1L;
    @NotNull(message = USER_ID_NOT_NULL)
    @Id
    private BigInteger id;
    private  transient Map<String,Object>  userData;
    private BigInteger documentId;
    private String status;
}
