package com.techsophy.tsf.account.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Objects;
import static com.techsophy.tsf.account.constants.AccountConstants.AUTHORIZATION;

/**
 * Utility class for retrieving the bearer token from the current HTTP request's authorization header.
 */
@Component
public class TokenConfig
{
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TokenConfig()
    {
    }

    /**
     * Retrieves the bearer token from the HTTP request's authorization header.
     *
     * @return the authorization header value (Bearer token) or null if not present
     * @throws NullPointerException if the request attributes are not available
     */
    public static String getBearerTokenHeader()
    {
        return ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes()))
                .getRequest()
                .getHeader(AUTHORIZATION);
    }
}
