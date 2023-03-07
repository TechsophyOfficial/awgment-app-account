package com.techsophy.tsf.account.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuditableData
{
    private String createdById;
    private Instant createdOn;
    private String updatedById;
    private Instant updatedOn;
}
