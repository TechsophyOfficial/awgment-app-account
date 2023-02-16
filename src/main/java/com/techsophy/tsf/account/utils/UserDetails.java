package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.exception.UserNameValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@RefreshScope
@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserDetails
{
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final ObjectMapper objectMapper;
    private final WebClientWrapper webClientWrapper;
    @Value(GATEWAY_URI)
    String gatewayApi;

    public void userNameValidations(String userName)
    {
        if(userName.startsWith(SERVICE_ACCOUNT))
        {
            log.error(USER_NAME_VALIDATION_EXCEPTION);
            throw new UserNameValidationException(USER_NAME_VALIDATION_EXCEPTION,globalMessageSource.get(USER_NAME_VALIDATION_EXCEPTION,userName));
        }
    }

    public List<Map<String, Object>> getUserDetails() throws JsonProcessingException
    {
        Map<String,Object> response;
        List<Map<String, Object>> userDetailsResponse;
        WebClient webClient;
        String loggedInUserId = tokenUtils.getLoggedInUserId();
        if (StringUtils.isEmpty(loggedInUserId))
        {
            throw new InvalidInputException(LOGGED_IN_USER_NOT_FOUND,globalMessageSource.get(LOGGED_IN_USER_NOT_FOUND,loggedInUserId));
        }
        String token = tokenUtils.getTokenFromContext();
        if (StringUtils.isNotEmpty(token))
        {
            webClient = webClientWrapper.createWebClient(token);
        }
        else
        {
            throw new InvalidInputException(TOKEN_NOT_NULL,globalMessageSource.get(TOKEN_NOT_NULL,loggedInUserId));
        }
        String userDetails = webClientWrapper.webclientRequest(webClient,gatewayApi + ACCOUNT_URL + FILTER_COLUMN+ loggedInUserId+ONLY_MANDATORY_FIELDS_TRUE,GET,null);
        if (StringUtils.isEmpty(userDetails) || userDetails.isEmpty())
        {
            throw new InvalidInputException(USER_DETAILS_NOT_FOUND,globalMessageSource.get(USER_DETAILS_NOT_FOUND,userDetails));
        }
        response = this.objectMapper.readValue(userDetails, new TypeReference<>()
        {
        });
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get(DATA);
        if (!data.isEmpty())
        {
            userDetailsResponse = this.objectMapper.convertValue(response.get(DATA), List.class);
            return userDetailsResponse;
        }
        throw new UserDetailsIdNotFoundException(USER_NOT_FOUND_BY_ID,globalMessageSource.get(USER_NOT_FOUND_BY_ID,loggedInUserId));
    }
}


