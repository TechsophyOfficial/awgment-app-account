package com.techsophy.tsf.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JsonRequestSizeLimitFilterTest {

    @Test
    void shouldPassThroughNonJsonContentTypesAndValidateMediaTypes() throws Exception {
        AwgmentSecurityProperties props = new AwgmentSecurityProperties();
        props.setMaxJsonBodySize(DataSize.ofBytes(64));
        assertThat(props.effectiveMaxJsonBodyBytes()).isEqualTo(1024L);
        props.setMaxJsonBodySize(DataSize.ofKilobytes(2));
        assertThat(props.effectiveMaxJsonBodyBytes()).isEqualTo(2048L);
        props.setMaxJsonBodySize(null);
        assertThat(props.effectiveMaxJsonBodyBytes()).isEqualTo(256L * 1024L);
        props.setMaxJsonBodySize(DataSize.ofBytes(0));
        assertThat(props.effectiveMaxJsonBodyBytes()).isEqualTo(1024L);

        assertThat(JsonRequestSizeLimitFilter.isJsonContentType(null)).isFalse();
        assertThat(JsonRequestSizeLimitFilter.isJsonContentType("  ")).isFalse();
        assertThat(JsonRequestSizeLimitFilter.isJsonContentType("text/plain")).isFalse();
        assertThat(JsonRequestSizeLimitFilter.isJsonContentType("application/json; charset=UTF-8")).isTrue();
        assertThat(JsonRequestSizeLimitFilter.isJsonContentType("application/problem+json")).isTrue();
        assertThat(JsonRequestSizeLimitFilter.isJsonContentType("application/xml")).isFalse();
        assertThat(JsonRequestSizeLimitFilter.isJsonContentType("text/foo+json")).isFalse();

        props.setMaxJsonBodySize(DataSize.ofBytes(1024));
        JsonRequestSizeLimitFilter filter = new JsonRequestSizeLimitFilter(new ObjectMapper(), props);

        MockHttpServletRequest textReq = new MockHttpServletRequest("POST", "/accounts/v2/users");
        textReq.setContentType("multipart/form-data");
        textReq.setContent("x".repeat(1500).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse textRes = new MockHttpServletResponse();
        AtomicBoolean calledSameReq = new AtomicBoolean(false);
        filter.doFilter(textReq, textRes, (req, res) -> calledSameReq.set(req == textReq));
        assertThat(calledSameReq.get()).isTrue();
    }

    @Test
    void shouldRejectOversizedContentLengthAndChunkedByteAndReaderReadsWith413() throws Exception {
        AwgmentSecurityProperties props = new AwgmentSecurityProperties();
        props.setMaxJsonBodySize(DataSize.ofBytes(1024));
        ObjectMapper mapper = new ObjectMapper();
        JsonRequestSizeLimitFilter filter = new JsonRequestSizeLimitFilter(mapper, props);

        // 1. Upfront Content-Length > limit -> immediate 413 ProblemDetail + OWASP headers
        MockHttpServletRequest hugeHeaderReq = new MockHttpServletRequest("POST", "/accounts/v2/users");
        hugeHeaderReq.setContentType("application/problem+json; charset=utf-8");
        hugeHeaderReq.setContent(("{\"hello\":\"" + "x".repeat(1100) + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse hugeRes = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.doFilter(hugeHeaderReq, hugeRes, (req, res) -> chainInvoked.set(true));
        assertThat(chainInvoked.get()).isFalse();
        assertThat(hugeRes.getStatus()).isEqualTo(413);
        assertThat(hugeRes.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(hugeRes.getContentAsString()).contains("\"errorCode\":\"PAYLOAD_TOO_LARGE\"");

        // 2. Chunked stream (unknown Content-Length = -1) exceeding cap on read(byte[])
        MockHttpServletRequest chunkedReq = unknownLengthRequest(
                "application/json", "{\"k\":\"" + "y".repeat(1100) + "\"}");
        MockHttpServletResponse chunkedRes = new MockHttpServletResponse();
        FilterChain readArrayChain = (req, res) -> {
            assertThat(req.getInputStream().isFinished()).isFalse();
            assertThat(req.getInputStream().isReady()).isTrue();
            ReadListener listener = mock(ReadListener.class);
            req.getInputStream().setReadListener(listener);
            byte[] buf = new byte[1200];
            assertThatThrownBy(() -> req.getInputStream().read(buf))
                    .isInstanceOf(JsonPayloadTooLargeException.class);
        };
        filter.doFilter(chunkedReq, chunkedRes, readArrayChain);

        // 3. Single-byte read() within cap and exceeding cap + getReader() caching/charset branches
        MockHttpServletRequest singleByteOk = unknownLengthRequest("application/json", "{\"a\":1}");
        singleByteOk.setCharacterEncoding("ISO-8859-1");
        filter.doFilter(singleByteOk, new MockHttpServletResponse(), (req, res) -> {
            ServletInputStream in = req.getInputStream();
            assertThat(req.getInputStream()).isSameAs(in);
            int total = 0;
            while (in.read() != -1) {
                total++;
            }
            assertThat(total).isEqualTo(7);
            assertThat(in.read(new byte[4], 0, 4)).isEqualTo(-1);
        });

        MockHttpServletRequest readerTooLong = unknownLengthRequest(
                "application/json", "{\"tooLong\":\"" + "z".repeat(1100) + "\"}");
        filter.doFilter(readerTooLong, new MockHttpServletResponse(), (req, res) -> {
            BufferedReader r1 = req.getReader();
            assertThat(req.getReader()).isSameAs(r1);
            assertThatThrownBy(r1::readLine)
                    .isInstanceOf(JsonPayloadTooLargeException.class);
        });

        for (String enc : new String[]{null, "   "}) {
            MockHttpServletRequest defaultCharsetReq = unknownLengthRequest("application/json", "{}");
            defaultCharsetReq.setCharacterEncoding(enc);
            filter.doFilter(defaultCharsetReq, new MockHttpServletResponse(), (req, res) -> {
                assertThat(req.getReader().readLine()).isEqualTo("{}");
            });
        }

        // 4. GlobalExceptionHandler fallback when Jackson wraps JsonPayloadTooLargeException
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertThat(handler.handleMessageNotReadable(
                new org.springframework.http.converter.HttpMessageNotReadableException(
                        "too large",
                        new JsonPayloadTooLargeException("Exceeded"),
                        new org.springframework.mock.http.MockHttpInputMessage(new byte[0])),
                hugeHeaderReq).getStatusCode().value()).isEqualTo(413);
    }

    private static MockHttpServletRequest unknownLengthRequest(String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new MockHttpServletRequest("POST", "/accounts/v2/users") {
            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }

            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return bais.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return bais.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        try {
                            readListener.onAllDataRead();
                        } catch (IOException ignored) {
                        }
                    }
                };
            }
        };
    }
}
