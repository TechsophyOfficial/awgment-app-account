package com.techsophy.tsf.account.service.impl;

import com.techsophy.tsf.account.constants.AccountConstants;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

public enum Rules implements IRule
{
    USERS {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
            List<String> userIds= (List<String>) data;
            String loggedInUser= String.valueOf(userDetailsFromKeycloak.get(PREFERED_USERNAME));
            return userIds.contains(loggedInUser);
        }
    },
    GROUPS {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
            List<String> groupIds= (List<String>) data;
            List<String> loggedInGroupsList= (List<String>) userDetailsFromKeycloak.get(AccountConstants.GROUPS);
            loggedInGroupsList=loggedInGroupsList.stream().map(x->x.substring(1)).collect(Collectors.toList());
            return loggedInGroupsList.stream().anyMatch(groupIds::contains);
        }
    },
    SYSTEM {
        @Override
        public boolean match(Object data, Map<String, Object> userDetailsFromKeycloak, Map<?, ?> context)
        {
            String userName= String.valueOf(userDetailsFromKeycloak.get(PREFERED_USERNAME));
            return userName.startsWith(PREFIX);
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
            List<String> loggedInRolesList= (List<String>) userDetailsFromKeycloak.get(CLIENT_ROLES);
            return loggedInRolesList.stream().anyMatch(rolesList::contains);
        }
    }
}
