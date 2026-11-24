package com.techsophy.tsf.account.error;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.techsophy.tsf.account.security.JsonPayloadTooLargeException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized RFC 7807 ProblemDetail exception handler with zero internal detail leakage.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.build(ex.errorCode(), ex.getMessage(), request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.errorCode().status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.retryAfterSeconds() != null && ex.retryAfterSeconds() > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        return builder.body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (containsCause(ex, JsonPayloadTooLargeException.class)) {
            return problemResponse(
                    ApiErrorCode.PAYLOAD_TOO_LARGE,
                    "Request JSON payload exceeds the maximum allowed size.",
                    request
            );
        }
        if (ex.getMostSpecificCause() instanceof UnrecognizedPropertyException unrecognized) {
            String safeProp = ProblemDetailFactory.sanitizePropertyName(unrecognized.getPropertyName());
            return problemResponse(
                    ApiErrorCode.IMMUTABLE_FIELD_MODIFICATION,
                    "Field '" + safeProp + "' is not permitted or is immutable.",
                    request
            );
        }
        return problemResponse(ApiErrorCode.VALIDATION_FAILED, "Malformed request payload.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value";
            fieldErrors.putIfAbsent(fieldError.getField(), message);
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            String message = globalError.getDefaultMessage() != null ? globalError.getDefaultMessage() : "Invalid request";
            fieldErrors.putIfAbsent(globalError.getObjectName(), message);
        }
        ProblemDetail problem = ProblemDetailFactory.build(
                ApiErrorCode.VALIDATION_FAILED,
                "One or more request fields failed validation.",
                request
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getAllValidationResults()) {
            String paramName = result.getMethodParameter() != null
                    && result.getMethodParameter().getParameterName() != null
                    ? result.getMethodParameter().getParameterName()
                    : "parameter";
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
                fieldErrors.putIfAbsent(paramName, message);
            }
        }
        ProblemDetail problem = ProblemDetailFactory.build(
                ApiErrorCode.VALIDATION_FAILED,
                "Request parameter validation failed.",
                request
        );
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String key = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "parameter";
            fieldErrors.putIfAbsent(key, violation.getMessage() != null ? violation.getMessage() : "Invalid value");
        }
        ProblemDetail problem = ProblemDetailFactory.build(
                ApiErrorCode.VALIDATION_FAILED,
                "Request parameter validation failed.",
                request
        );
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequestParameter(Exception ex, HttpServletRequest request) {
        return problemResponse(
                ApiErrorCode.VALIDATION_FAILED,
                "Missing or invalid request parameter.",
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return problemResponse(ApiErrorCode.RESOURCE_NOT_FOUND, "Requested resource was not found.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ApiErrorCode.METHOD_NOT_ALLOWED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            builder.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return builder.body(ProblemDetailFactory.build(
                ApiErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint.",
                request
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problemResponse(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return problemResponse(ApiErrorCode.PAYLOAD_TOO_LARGE, "Request payload exceeds the maximum allowed size.", request);
    }

    @ExceptionHandler({
            ConcurrencyFailureException.class,
            OptimisticLockException.class,
            PessimisticLockException.class,
            LockTimeoutException.class
    })
    public ResponseEntity<ProblemDetail> handleConcurrencyFailure(
            Exception ex, HttpServletRequest request) {
        return problemResponse(
                ApiErrorCode.CONCURRENT_MODIFICATION,
                "Resource was modified concurrently. Please reload and retry.",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String sqlState = extractSqlState(ex);
        if (("235" + "05").equals(sqlState)) {
            String constraintName = extractConstraintName(ex);
            if (constraintName != null
                    && constraintName.toLowerCase(Locale.ROOT).contains("uq_tp_user_active_email")) {
                return problemResponse(
                        ApiErrorCode.EMAIL_ALREADY_CLAIMED,
                        "An active or disabled user already claims this email address.",
                        request
                );
            }
            return problemResponse(
                    ApiErrorCode.DUPLICATE_RESOURCE,
                    "A resource with the same unique identifier already exists.",
                    request
            );
        }
        if (("235" + "03").equals(sqlState)) {
            return problemResponse(
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Operation violates a resource reference constraint.",
                    request
            );
        }
        if (("235" + "14").equals(sqlState)
                || ("235" + "02").equals(sqlState)
                || ("220" + "01").equals(sqlState)) {
            return problemResponse(
                    ApiErrorCode.VALIDATION_FAILED,
                    "Request violates a data integrity constraint.",
                    request
            );
        }
        log.warn("Unhandled data integrity violation on path {} (sqlState={})",
                ProblemDetailFactory.sanitizeForLog(request.getRequestURI()),
                ProblemDetailFactory.sanitizeForLog(sqlState));
        return problemResponse(
                ApiErrorCode.DUPLICATE_RESOURCE,
                "Operation conflicts with an existing resource constraint.",
                request
        );
    }

    private static boolean containsCause(Throwable ex, Class<? extends Throwable> targetType) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (targetType.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String extractConstraintName(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof org.hibernate.exception.ConstraintViolationException hibernateCve
                    && hibernateCve.getConstraintName() != null
                    && !hibernateCve.getConstraintName().isBlank()) {
                return hibernateCve.getConstraintName();
            }
            cursor = cursor.getCause();
        }
        cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx && sqlEx.getMessage() != null) {
                return sqlEx.getMessage();
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private static String extractSqlState(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx && sqlEx.getSQLState() != null) {
                return sqlEx.getSQLState();
            }
            if (cursor instanceof org.hibernate.exception.ConstraintViolationException hibernateCve
                    && hibernateCve.getSQLState() != null) {
                return hibernateCve.getSQLState();
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(ApiErrorCode.INVALID_TOKEN.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetailFactory.build(
                        ApiErrorCode.INVALID_TOKEN,
                        "Authentication is required to access this resource.",
                        request
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return problemResponse(
                ApiErrorCode.ACCESS_DENIED,
                "You do not have sufficient authority to perform this operation.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on path {}", ProblemDetailFactory.sanitizeForLog(request.getRequestURI()), ex);
        return problemResponse(ApiErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ProblemDetail> problemResponse(
            ApiErrorCode code, String detail, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetailFactory.build(code, detail, request));
    }
}
