package com.techsophy.tsf.account.dto;



import lombok.Data;

import java.util.Map;

@Data
public class RequestProperties {
    private String username;
    private Map<String,?> user;
    private String userId;
    private Map<String,String> context;
    private Map<String, Object> userInfo;
}

