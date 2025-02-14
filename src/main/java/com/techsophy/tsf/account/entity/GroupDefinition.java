package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TP_GROUP_COLLECTION)
public class GroupDefinition extends Auditable
{
    private static final long serialVersionUID = 1L;
    @NotNull(message = ID_NOT_NULL)
    @Id
    private BigInteger id;
    private String name;
    @NotBlank(message = DESCRIPTION_NOT_BLANK)
    private String description;
    private String groupId;
}
