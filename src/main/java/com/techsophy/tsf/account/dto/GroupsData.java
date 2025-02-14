package com.techsophy.tsf.account.dto;

import lombok.Value;
import lombok.With;
import jakarta.validation.constraints.NotBlank;
import static com.techsophy.tsf.account.constants.AccountConstants.GROUP_ID_NOT_BLANK;

@With
@Value
public class GroupsData
{
    String id;
    String name;
    String description;
    @NotBlank(message = GROUP_ID_NOT_BLANK) String groupId;
}
