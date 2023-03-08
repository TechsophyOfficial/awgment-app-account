package com.techsophy.tsf.account.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.springframework.data.annotation.*;
import java.math.BigInteger;
import java.time.Instant;

@Data
@With
@NoArgsConstructor
@AllArgsConstructor
public class Auditable
{
    @CreatedBy
    private BigInteger createdById;
    @LastModifiedBy
    private BigInteger updatedById;
    @CreatedDate
    private Instant createdOn;
    @LastModifiedDate
    private Instant updatedOn;
}
