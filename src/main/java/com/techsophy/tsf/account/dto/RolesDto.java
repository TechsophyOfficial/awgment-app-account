package com.techsophy.tsf.account.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RolesDto {
    @NotNull(message ="name should not be null")
    String name;
    String description;
}
