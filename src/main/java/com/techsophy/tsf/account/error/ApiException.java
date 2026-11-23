package com.techsophy.tsf.account.error;

import java.util.Objects;

/**
 * Domain exception mapped directly to RFC 7807 ProblemDetail by GlobalExceptionHandler.
 */
public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final Long retryAfterSeconds;

    public ApiException(ApiErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public ApiException(ApiErrorCode errorCode, String detail, Long retryAfterSeconds) {
        super(detail);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException rateLimited(String detail, long retryAfterSeconds) {
        return new ApiException(ApiErrorCode.RATE_LIMIT_EXCEEDED, detail, retryAfterSeconds);
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
