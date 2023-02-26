package com.techsophy.tsf.account.dto;

import com.techsophy.tsf.account.entity.Auditable;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@With
@Value
public class BulkUserResponse extends Auditable
{
    String id;
    Map<String,Object> userData;
    String documentId;
    String status;
}
