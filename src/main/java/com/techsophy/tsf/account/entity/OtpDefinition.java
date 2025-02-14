package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Date;
import static com.techsophy.tsf.account.constants.AccountConstants.ID_NOT_NULL;
import static com.techsophy.tsf.account.constants.AccountConstants.TP_OTP_COLLECTION;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TP_OTP_COLLECTION )
public class OtpDefinition extends Auditable
{
    @NotNull(message = ID_NOT_NULL)
    @Id
    private BigInteger id;
    private String channel;
    private String to;
    private String otp;
    private Date expiredAt;
}
