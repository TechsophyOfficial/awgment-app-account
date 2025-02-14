package com.techsophy.tsf.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import java.math.BigInteger;
import static com.techsophy.tsf.account.constants.AccountConstants.TP_THEME_COLLECTION;
import static com.techsophy.tsf.account.constants.AccountConstants.THEME_NAME_NOT_BLANK;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TP_THEME_COLLECTION)
public class ThemesDefinition extends Auditable
{
    @Id
    private BigInteger id;
    @NotBlank(message = THEME_NAME_NOT_BLANK)
    private String name;
    private Object  content;
}
