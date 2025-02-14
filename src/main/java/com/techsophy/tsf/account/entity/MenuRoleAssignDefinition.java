package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.List;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection=MENU_ROLE_TABLE)
public class MenuRoleAssignDefinition extends Auditable  {
    @NotNull(message = ID_NOT_NULL)
    @Id
    private BigInteger id;
    private String role;
    private List<String> menus;
    @NotNull(message = VERSION_NOT_NULL)
    private Integer version;
}
