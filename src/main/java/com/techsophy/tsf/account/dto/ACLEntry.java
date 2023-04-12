package com.techsophy.tsf.account.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.service.impl.Rules;
import lombok.Data;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;
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

        return  ruleType.match(this.data, request.getUserInfo(),context)?new ACLDecision(decision, renderAdditionalDetails(request)):null;
    }


    private Map<String, Object> renderAdditionalDetails(RequestProperties request)
    {
        ObjectMapper mapper = new ObjectMapper();
        ST detailTemplate = new ST(new JSONObject(this.additionalDetails).toString(), '<', '>');
        detailTemplate.add("request",request);
        JsonNode objectMap = null;
        try {
            objectMap = mapper.readTree(detailTemplate.render());
        } catch (JsonProcessingException e) {
            throw new RunTimeException(e.getMessage());
        }
        return  mapper.convertValue(objectMap, Map.class);
    }
}
