package com.techsophy.tsf.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ACLDecision
{
    String decision;
    Map<String,Object> additionalDetails;
}
