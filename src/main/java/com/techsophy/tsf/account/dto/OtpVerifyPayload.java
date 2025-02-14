package com.techsophy.tsf.account.dto;

import lombok.Value;
import lombok.With;
import jakarta.validation.constraints.NotBlank;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@With
@Value
public class OtpVerifyPayload
{
    @NotBlank(message =TYPE_SHOULD_NOT_BE_BLANK)
    String channel;
    @NotBlank(message = TO_SHOULD_NOT_BE_BLANK)
    String to;
    @NotBlank(message = OTP_SHOULD_NOT_BE_BLANK)
    String otp;
}
