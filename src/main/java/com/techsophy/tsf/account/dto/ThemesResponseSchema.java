package com.techsophy.tsf.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Value;
import lombok.With;
import javax.validation.constraints.NotBlank;
import java.time.Instant;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@With
@Value
public class ThemesResponseSchema
{
    String id;
    @NotBlank(message = NAME_NOT_BLANK) String name;
    Object content;
    String createdById;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone = TIME_ZONE)
    Instant createdOn;
    String updatedById;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone = TIME_ZONE)
    Instant updatedOn;
}
