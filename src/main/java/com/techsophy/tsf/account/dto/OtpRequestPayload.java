package com.techsophy.tsf.account.dto;

import lombok.Value;
import lombok.With;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.TO_SHOULD_NOT_BE_BLANK;
import static com.techsophy.tsf.account.constants.AccountConstants.TYPE_SHOULD_NOT_BE_BLANK;

@With
@Value
public class OtpRequestPayload
{
     @NotBlank(message =TYPE_SHOULD_NOT_BE_BLANK)
     String channel;
     @NotBlank(message = TO_SHOULD_NOT_BE_BLANK)
     String to;
     String from;
     String subject;
     String cc;
     String body;
     String templateId;
     Map<String,String> templateData;
}
