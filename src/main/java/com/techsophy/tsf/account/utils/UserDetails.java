package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.exception.UserNameValidationException;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@RefreshScope
@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class UserDetails  implements AuditorAware<BigInteger>
{
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final ObjectMapper objectMapper;
    private final UserServiceImpl userServiceImpl;
    @Value(GATEWAY_URI)
    String gatewayApi;
    private static final ThreadLocal<BigInteger> USER_ID = new ThreadLocal<>();


    public void userNameValidations(String userName)
    {
        if(userName.startsWith(SERVICE_ACCOUNT))
        {
            log.error(USER_NAME_VALIDATION_EXCEPTION);
            throw new UserNameValidationException(USER_NAME_VALIDATION_EXCEPTION,globalMessageSource.get(USER_NAME_VALIDATION_EXCEPTION,userName));
        }
    }

    public Optional<BigInteger> getUserId()
    {
        if(tokenUtils.getTokenFromContext()!=null)
        {
            try{
                SecurityContext context = SecurityContextHolder.getContext();
                Authentication authentication = context.getAuthentication();
                Object principal = authentication.getPrincipal();
                Jwt jwt = (Jwt) principal;
                Map<String,Object> claims=jwt.getClaims();
                if(claims.containsKey("userId"))
                {
                    return Optional.ofNullable(BigInteger.valueOf(Long.valueOf(String.valueOf(claims.get("userId")))));
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    public List<Map<String, Object>> getUserDetails()
    {
        List<Map<String, Object>> userDetailsResponse;
        String loggedInUserId = tokenUtils.getLoggedInUserId();
        if (StringUtils.isEmpty(loggedInUserId))
        {
            throw new InvalidInputException(LOGGED_IN_USER_NOT_FOUND,globalMessageSource.get(LOGGED_IN_USER_NOT_FOUND,loggedInUserId));
        }
        List<AuditableData> userDetails = userServiceImpl.getAllUsersByFilter("loginId",loggedInUserId);
        if(userDetails!=null)
        {
            userDetailsResponse = objectMapper.convertValue(userDetails,List.class);
            return userDetailsResponse;
        }
        throw new UserDetailsIdNotFoundException(USER_NOT_FOUND_BY_ID,globalMessageSource.get(USER_NOT_FOUND_BY_ID,loggedInUserId));
    }

    @SneakyThrows
    @Override
    public Optional<BigInteger> getCurrentAuditor()
    {
        try
        {
            Optional<BigInteger> userId = Optional.ofNullable(USER_ID.get());
            if(userId.isEmpty()) {
                userId=getUserId();
                if(userId.isEmpty())
                {
                    userId = Optional.ofNullable(BigInteger.valueOf(Long.parseLong(String.valueOf(getUserDetails().get(0).get(ID)))));
                }
                if(userId.isPresent()) {
                    USER_ID.set(userId.get());
                }
            }
            return userId;
        }
        catch (Exception e)
        {
            return Optional.empty();
        }
    }
    public void unload() {
        USER_ID.remove(); // Compliant
    }
}


