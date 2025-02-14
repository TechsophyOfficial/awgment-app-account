package com.techsophy.tsf.account.dto;

import lombok.Value;
import lombok.With;
import jakarta.validation.constraints.NotBlank;
import static com.techsophy.tsf.account.constants.AccountConstants.NAME_NOT_BLANK;

@With
@Value
public class ThemesSchema
{
    String id;
    @NotBlank(message = NAME_NOT_BLANK) String name;
    Object content;
}
