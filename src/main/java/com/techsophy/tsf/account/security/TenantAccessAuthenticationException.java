package com.techsophy.tsf.account.security;

import com.techsophy.tsf.account.error.ApiErrorCode;
import org.springframework.security.core.AuthenticationException;

import java.util.Objects;

/**
 * Security filter-chain exception carrying an {@link ApiErrorCode} (such as
 * {@link ApiErrorCode#TENANT_NOT_ACTIVE} or {@link ApiErrorCode#TENANT_SUSPENDED})
 * so the authentication entry point renders an exact RFC 7807 HTTP 403 ProblemDetail.
 */
public class TenantAccessAuthenticationException extends AuthenticationException {

    private final ApiErrorCode errorCode;

    public TenantAccessAuthenticationException(ApiErrorCode errorCode, String message) {
        super(normalizeMessage(errorCode, message));
        this.errorCode = errorCode;
    }

    public TenantAccessAuthenticationException(ApiErrorCode errorCode, String message, Throwable cause) {
        super(normalizeMessage(errorCode, message), cause);
        this.errorCode = errorCode;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }

    private static String normalizeMessage(ApiErrorCode errorCode, String message) {
        ApiErrorCode nonNullCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        return (message == null || message.isBlank()) ? nonNullCode.title() : message;
    }
}
