package com.techsophy.tsf.account.dto;

import com.techsophy.tsf.account.service.impl.Rules;
import lombok.Data;
import java.util.Map;

@Data
public class ACLEntry
{
    String decision;
    Rules ruleType ;
    Object data;
    Map<String,String> additionalDetails;

    public Decision evaluateDecision(Map<String,Object> userDetailsFromKeycloak,Map<?,?> context)
    {
        return  ruleType.match(this.data,userDetailsFromKeycloak,context)?new Decision(decision,null):null;
    }
}
