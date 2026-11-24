package com.techsophy.tsf.account.security;

import java.io.IOException;

/**
 * Thrown by {@link JsonRequestSizeLimitFilter} when a streamed JSON request body exceeds
 * the configured maximum allowed byte size without a pre-declared {@code Content-Length}.
 */
public class JsonPayloadTooLargeException extends IOException {

    public JsonPayloadTooLargeException(String message) {
        super(message);
    }
}
