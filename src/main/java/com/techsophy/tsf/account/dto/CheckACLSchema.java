package com.techsophy.tsf.account.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CheckACLSchema
{
    Map<String,String> context;
}
