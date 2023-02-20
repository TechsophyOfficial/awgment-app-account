package com.techsophy.tsf.account.entity;

import com.techsophy.tsf.account.dto.ACLEntry;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigInteger;
import java.util.List;

import static com.techsophy.tsf.account.constants.AccountConstants.TP_ACL;

@EqualsAndHashCode(callSuper = true)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TP_ACL)
public class ACLDefinition extends Auditable
{
    private static final long serialVersionUID = 1L;
    @Id
    private BigInteger id;
    @Indexed(unique = true)
    private String name;
    private List<ACLEntry> read;
    private List<ACLEntry> update;
    private List<ACLEntry> delete;
}
