package com.techsophy.tsf.account.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Shared RFC 7807 ProblemDetail builder with safe RFC 3986 URI parsing,
 * CRLF log-forging/property sanitization, and UTF-8 servlet response writing.
 */
public final class ProblemDetailFactory {

    private static final URI ROOT_URI = URI.create("/");
    private static final int MAX_SANITIZED_LENGTH = 256;
    private static final int MAX_PROPERTY_NAME_LENGTH = 64;

    private ProblemDetailFactory() {
    }

    public static ProblemDetail build(ApiErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setInstance(safeInstanceUri(request != null ? request.getRequestURI() : null));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    public static void writeServletProblem(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            ApiErrorCode code,
            String detail,
            Map<String, String> extraHeaders) throws IOException {
        ProblemDetail problem = build(code, detail, request);
        response.setStatus(code.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE + ";charset=UTF-8");
        extraHeaders.forEach(response::setHeader);
        objectMapper.writeValue(response.getWriter(), problem);
    }

    public static URI safeInstanceUri(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            return ROOT_URI;
        }
        try {
            return URI.create(rawUri);
        } catch (IllegalArgumentException ex) {
            return ROOT_URI;
        }
    }

    public static String sanitizeForLog(String raw) {
        return sanitize(raw, "/", MAX_SANITIZED_LENGTH);
    }

    public static String sanitizePropertyName(String raw) {
        return sanitize(raw, "unknown", MAX_PROPERTY_NAME_LENGTH);
    }

    private static String sanitize(String raw, String blankFallback, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return blankFallback;
        }
        String collapsed = raw.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", "_");
        return collapsed.length() > maxLength
                ? collapsed.substring(0, maxLength)
                : collapsed;
    }
}
