package com.techsophy.tsf.account.dto;

import lombok.Data;

@Data
public class ACLValidate
{
    String name;
    Decision read;
    Decision update;
    Decision delete;
}
