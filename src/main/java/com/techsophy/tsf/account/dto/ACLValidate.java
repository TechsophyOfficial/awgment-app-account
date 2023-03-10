package com.techsophy.tsf.account.dto;

import lombok.Data;

@Data
public class ACLValidate
{
    String name;
    ACLDecision read;
    ACLDecision update;
    ACLDecision delete;
}
