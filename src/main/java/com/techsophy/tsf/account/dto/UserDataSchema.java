package com.techsophy.tsf.account.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import lombok.With;

import java.util.Map;

@With
@Value
public class UserDataSchema
{
    @NotNull
    Map<String,Object> userData;
    String userId;

    @JsonCreator
    public UserDataSchema(
            @JsonProperty("userData") Map<String, Object> userData,
            @JsonProperty("userId") String userId
    ) {
        this.userData = userData;
        this.userId = userId;
    }
}
