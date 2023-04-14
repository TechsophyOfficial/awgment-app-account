package com.techsophy.tsf.account.dto;



import lombok.Data;

import java.util.Map;

@Data
public class RequestProperties {
    private String username;
    private String firstName;
    private String lastName;
    private String mobileNumber;
    private String emailId;
    private String department;
    private String userId;
    private Map<String,String> context;
    private Map<String, Object> userInfo;
}

