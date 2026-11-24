package com.techsophy.tsf.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Enforces bounded JSON request body sizes (OWASP API4 DoS defense) both upfront via
 * {@code Content-Length} and on-the-fly via a byte-counting {@link ServletInputStream}
 * wrapper for chunked requests.
 */
@Component
public class JsonRequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Map<String, String> SECURITY_HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate",
            "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'",
            "Referrer-Policy", "no-referrer",
            "Permissions-Policy", "geolocation=(), microphone=(), camera=()"
    );

    private final ObjectMapper objectMapper;
    private final long maxJsonBodyBytes;

    public JsonRequestSizeLimitFilter(ObjectMapper objectMapper, AwgmentSecurityProperties securityProperties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.maxJsonBodyBytes = Objects.requireNonNull(securityProperties, "securityProperties must not be null")
                .effectiveMaxJsonBodyBytes();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isJsonContentType(request.getContentType())) {
            filterChain.doFilter(request, response);
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxJsonBodyBytes) {
            ProblemDetailFactory.writeServletProblem(
                    objectMapper,
                    request,
                    response,
                    ApiErrorCode.PAYLOAD_TOO_LARGE,
                    "Request JSON payload exceeds the maximum allowed size.",
                    SECURITY_HEADERS
            );
            return;
        }
        filterChain.doFilter(new BoundedJsonRequestWrapper(request, maxJsonBodyBytes), response);
    }

    static boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int semi = lower.indexOf(';');
        String mediaType = (semi >= 0 ? lower.substring(0, semi) : lower).trim();
        return "application/json".equals(mediaType)
                || (mediaType.startsWith("application/") && mediaType.endsWith("+json"));
    }

    private static final class BoundedJsonRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream boundedStream;
        private BufferedReader reader;

        BoundedJsonRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (boundedStream == null) {
                boundedStream = new BoundedServletInputStream(super.getInputStream(), maxBytes);
            }
            return boundedStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = (encoding != null && !encoding.isBlank())
                        ? Charset.forName(encoding)
                        : StandardCharsets.UTF_8;
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    private static final class BoundedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        BoundedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                incrementAndCheck(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count > 0) {
                incrementAndCheck(count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void incrementAndCheck(int delta) throws JsonPayloadTooLargeException {
            bytesRead += delta;
            if (bytesRead > maxBytes) {
                throw new JsonPayloadTooLargeException(
                        "Request JSON payload exceeds the maximum allowed size."
                );
            }
        }
    }
}
