package com.techsophy.tsf.account.dto;

import lombok.Value;
import lombok.With;
import java.time.Instant;
import java.util.List;

@With
@Value
public class GroupsDataSchema
{
    String id;
    String name;
    String description;
    String groupId;
    List<String> roles;
    String createdById;
    Instant createdOn;
    String updatedById;
    Instant updatedOn;
}
