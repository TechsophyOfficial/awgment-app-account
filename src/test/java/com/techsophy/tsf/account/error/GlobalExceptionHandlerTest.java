package com.techsophy.tsf.account.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_PENDING_APPROVAL"))
                .andExpect(jsonPath("$.detail").value("Account is pending administrator approval."))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    void shouldIncludeRetryAfterHeaderForPositiveRateLimitCooldown() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void shouldOmitRetryAfterHeaderWhenCooldownIsZeroOrNegative() throws Exception {
        mockMvc.perform(get("/test/rate-limit-zero"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));

        mockMvc.perform(get("/test/rate-limit-negative"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"))
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
    void shouldMapMalformedAndEmptyJsonBodyToValidationFailed() throws Exception {
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldMapMultipleAndDuplicateFieldValidationErrorsDeterministically() throws Exception {
        // Blank displayName violates @NotBlank, @Size, and @Pattern simultaneously; code violates @NotBlank
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
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

    @Test
    void everyApiErrorCodeShouldHaveUniqueTypeUriNonBlankTitleAndValidHttpStatus() {
        Set<URI> seenTypes = new HashSet<>();
        for (ApiErrorCode code : ApiErrorCode.values()) {
            assertThat(code.status()).isNotNull();
            assertThat(code.title()).isNotBlank();
            assertThat(code.type()).isNotNull();
            assertThat(code.type().toString()).startsWith("https://awgment.dev/errors/");
            assertThat(seenTypes.add(code.type()))
                    .as("Duplicate error type URI for %s", code.name())
                    .isTrue();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record StrictPayload(
            @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[a-zA-Z0-9 ]+$") String displayName,
            @NotBlank String code
    ) {
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

        @GetMapping("/test/rate-limit-zero")
        void rateLimitZero() {
            throw ApiException.rateLimited("Cooldown edge zero.", 0L);
        }

        @GetMapping("/test/rate-limit-negative")
        void rateLimitNegative() {
            throw ApiException.rateLimited("Cooldown edge negative.", -5L);
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
