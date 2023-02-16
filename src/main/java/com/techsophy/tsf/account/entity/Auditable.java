package com.techsophy.tsf.account.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import java.math.BigInteger;
import java.time.Instant;

@Data
@With
@NoArgsConstructor
@AllArgsConstructor
public class Auditable
{
    private BigInteger createdById;
    private BigInteger updatedById;
    @CreatedDate
    private Instant createdOn;
    @LastModifiedDate
    private Instant updatedOn;
}
