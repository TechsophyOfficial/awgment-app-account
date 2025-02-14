package com.techsophy.tsf.account.service;

import java.util.Map;
public interface IRule
{
    boolean match(Object data,Map<String,Object> userDetailsFromKeycloak,Map<?,?>context);
}
