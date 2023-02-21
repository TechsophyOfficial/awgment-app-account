package com.techsophy.tsf.account.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import static com.techsophy.tsf.account.constants.ErrorConstants.ACL_NAME_CANNOT_BE_BLANK;

@Data
public class ACLSchema
{
    String id;
    @NotBlank(message =ACL_NAME_CANNOT_BE_BLANK)
    String name;
    @NotNull(message="read cannot be null")
    List<ACLEntry> read;
    @NotNull(message="update cannot be null")
    List<ACLEntry> update;
    @NotNull(message="delete cannot be null")
    List<ACLEntry> delete;
}
