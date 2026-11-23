package com.techsophy.tsf.account.error;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized RFC 7807 ProblemDetail exception handler with zero internal detail leakage.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(ex.errorCode(), ex.getMessage(), request);
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
        if (ex.getCause() instanceof UnrecognizedPropertyException unrecognized) {
            ProblemDetail problem = buildProblem(
                    ApiErrorCode.IMMUTABLE_FIELD_MODIFICATION,
                    "Field '" + unrecognized.getPropertyName() + "' is not permitted or is immutable.",
                    request
            );
            return ResponseEntity.status(ApiErrorCode.IMMUTABLE_FIELD_MODIFICATION.status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        ProblemDetail problem = buildProblem(
                ApiErrorCode.VALIDATION_FAILED,
                "Malformed request payload.",
                request
        );
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = buildProblem(
                ApiErrorCode.VALIDATION_FAILED,
                "One or more request fields failed validation.",
                request
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(
                ApiErrorCode.CONCURRENT_MODIFICATION,
                "Resource was modified concurrently. Please reload and retry.",
                request
        );
        return ResponseEntity.status(ApiErrorCode.CONCURRENT_MODIFICATION.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(
                ApiErrorCode.ACCESS_DENIED,
                "You do not have sufficient authority to perform this operation.",
                request
        );
        return ResponseEntity.status(ApiErrorCode.ACCESS_DENIED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on path {}", request.getRequestURI(), ex);
        ProblemDetail problem = buildProblem(
                ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request
        );
        return ResponseEntity.status(ApiErrorCode.INTERNAL_ERROR.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ProblemDetail buildProblem(ApiErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
