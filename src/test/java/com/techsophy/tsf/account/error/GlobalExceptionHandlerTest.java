package com.techsophy.tsf.account.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper strictMapper = Jackson2ObjectMapperBuilder.json()
                .failOnUnknownProperties(true)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(strictMapper))
                .build();
    }

    @Test
    void shouldReturnRfc7807ProblemDetailForDomainApiException() throws Exception {
        mockMvc.perform(get("/test/pending"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_PENDING_APPROVAL"))
                .andExpect(jsonPath("$.detail").value("Account is pending administrator approval."))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    void shouldIncludeRetryAfterHeaderForRateLimitExceeded() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void shouldMapUnknownJsonPropertyToImmutableFieldModification() throws Exception {
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ok\",\"department\":\"Injected\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));
    }

    @Test
    void shouldMapMalformedJsonToValidationFailed() throws Exception {
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldMapValidationErrorsToValidationFailedProblemDetail() throws Exception {
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());
    }

    @Test
    void shouldMapOptimisticLockingFailureTo409ConcurrentModification() throws Exception {
        mockMvc.perform(get("/test/concurrent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void shouldMapSpringAccessDeniedTo403Forbidden() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldNeverLeakInternalExceptionOrSqlDetailsOnUnexpectedError() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail", not(containsString("SELECT * FROM secret"))));
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record StrictPayload(@NotBlank String displayName) {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/test/pending")
        void pending() {
            throw new ApiException(ApiErrorCode.ACCESS_PENDING_APPROVAL, "Account is pending administrator approval.");
        }

        @GetMapping("/test/rate-limit")
        void rateLimit() {
            throw ApiException.rateLimited("Please wait before requesting another email.", 60L);
        }

        @PostMapping("/test/strict-dto")
        String strictDto(@Valid @RequestBody StrictPayload payload) {
            return payload.displayName();
        }

        @GetMapping("/test/concurrent")
        void concurrent() {
            throw new OptimisticLockingFailureException("Row updated by another transaction");
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("Forbidden");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("SQL failure: SELECT * FROM secret_credentials");
        }
    }
}
