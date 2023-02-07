package com.techsophy.tsf.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import static com.techsophy.tsf.account.constants.AccountConstants.THEME_ID_NOT_NULL;


@Data
@AllArgsConstructor
public class UserPreferencesSchema
{
    String id;
    String userId;
    @NotBlank(message = THEME_ID_NOT_NULL)
    String themeId;
    String profilePicture;
}
