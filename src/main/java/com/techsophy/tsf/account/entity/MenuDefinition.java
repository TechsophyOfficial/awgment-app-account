package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection=MENU_TABLE)
public class MenuDefinition extends Auditable
{
    @NotNull(message = ID_NOT_NULL)
    @Id
    private BigInteger id;
    private String type;
    private String label;
    private String url;
    private boolean divider;
    @NotNull(message = VERSION_NOT_NULL)
    private Integer version;
}
