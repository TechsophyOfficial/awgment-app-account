package com.techsophy.tsf.account.error;

/**
 * Domain exception translated into RFC 7807 ProblemDetail by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final Long retryAfterSeconds;

    public ApiException(ApiErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ApiErrorCode errorCode, String message, Long retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException rateLimited(String message, long retryAfterSeconds) {
        return new ApiException(ApiErrorCode.RATE_LIMIT_EXCEEDED, message, retryAfterSeconds);
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
