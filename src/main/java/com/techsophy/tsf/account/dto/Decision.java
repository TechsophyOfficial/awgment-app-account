package com.techsophy.tsf.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Decision
{
    String decisionValue;
    String additionalDetails;
}
