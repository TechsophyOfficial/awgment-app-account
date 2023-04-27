package com.techsophy.tsf.account.dto;

import com.techsophy.tsf.account.service.impl.Rules;
import lombok.Data;
import java.util.Map;

@Data
public class ACLEntry
{
    String decision;
    Rules ruleType;
    Object data;
    Map<String,Object> additionalDetails;


    public ACLDecision evaluateDecision(RequestProperties request, Map<?,?> context)
    {

        return  ruleType.match(this.data, request.getUserInfo(),context)?new ACLDecision(decision, this.additionalDetails):null;
    }

}
