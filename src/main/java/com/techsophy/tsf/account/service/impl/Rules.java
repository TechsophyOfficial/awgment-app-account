package com.techsophy.tsf.account.service.impl;

import com.techsophy.tsf.account.constants.AccountConstants;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

public enum Rules implements IRule
{
    USERS {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
           return  ((List<String>) data).contains(String.valueOf(userDetailsFromKeycloak.get(PREFERED_USERNAME)));
        }
    },
    GROUPS {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
            List<String> groupIds= (List<String>) data;
            return ((List<String>)(userDetailsFromKeycloak.get(AccountConstants.GROUPS))).stream().map(x->x.substring(1)).anyMatch(groupIds::contains);
        }
    },
    SYSTEM {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
            return String.valueOf(userDetailsFromKeycloak.get(PREFERED_USERNAME)).startsWith(PREFIX);
        }
    },
    ALL {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
           return true;
        }
    },
    ROLES {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
            List<String> rolesList= (List<String>) data;
            return ((List<String>) userDetailsFromKeycloak.get(CLIENT_ROLES)).stream().anyMatch(rolesList::contains);
        }
    }
}
