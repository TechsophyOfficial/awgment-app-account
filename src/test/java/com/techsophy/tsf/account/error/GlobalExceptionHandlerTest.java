package com.techsophy.tsf.account.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void shouldMapUnknownJsonPropertyIncludingNestedDtoToImmutableFieldModification() throws Exception {
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ok\",\"code\":\"c1\",\"department\":\"Injected\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));

        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ok\",\"code\":\"c1\",\"nested\":{\"secret\":\"x\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));

        // Very long unknown property name (>64 chars) is truncated safely in ProblemDetail detail
        String longProp = "x".repeat(90);
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ok\",\"code\":\"c1\",\"" + longProp + "\":\"v\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"))
                .andExpect(jsonPath("$.detail", not(containsString(longProp))));

        // Blank/whitespace unknown property name normalizes safely to 'unknown' (never '/')
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ok\",\"code\":\"c1\",\"   \":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"))
                .andExpect(jsonPath("$.detail", containsString("Field 'unknown'")));
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
    void shouldMapStandardSpringMvc4xxExceptionsInsteadOf500() throws Exception {
        // 405 Method Not Allowed (must emit RFC 9110 Allow header)
        mockMvc.perform(delete("/test/strict-dto"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));

        // 415 Unsupported Media Type
        mockMvc.perform(post("/test/strict-dto")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));

        // 400 Missing required query parameter
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 400 Parameter type mismatch
        mockMvc.perform(get("/test/param").param("size", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 400 Missing required header (ServletRequestBindingException)
        mockMvc.perform(get("/test/missing-header"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 400 Missing multipart part (MissingServletRequestPartException)
        mockMvc.perform(get("/test/missing-part"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 406 Not Acceptable (HttpMediaTypeNotAcceptableException)
        mockMvc.perform(get("/test/strict-dto-json").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());

        // 400 ConstraintViolationException
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 404 NoResourceFoundException
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // 413 MaxUploadSizeExceededException
        mockMvc.perform(get("/test/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"));

        // 401 AuthenticationException
        mockMvc.perform(get("/test/auth-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
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
    void problemDetailFactoryShouldSanitizeCrlfTruncateLongInputsAndHandleInvalidUriSafely() {
        assertThat(ProblemDetailFactory.sanitizeForLog("/accounts/v2/users\r\nFAKE LOG\tENTRY"))
                .isEqualTo("/accounts/v2/users_FAKE LOG_ENTRY");
        assertThat(ProblemDetailFactory.sanitizeForLog(null)).isEqualTo("/");
        assertThat(ProblemDetailFactory.sanitizeForLog("   ")).isEqualTo("/");
        assertThat(ProblemDetailFactory.sanitizeForLog("a".repeat(300))).hasSize(256);

        assertThat(ProblemDetailFactory.safeInstanceUri("/accounts/v2/users")).isEqualTo(URI.create("/accounts/v2/users"));
        assertThat(ProblemDetailFactory.safeInstanceUri("/accounts/{bad uri}")).isEqualTo(URI.create("/"));
        assertThat(ProblemDetailFactory.safeInstanceUri("   ")).isEqualTo(URI.create("/"));
        assertThat(ProblemDetailFactory.safeInstanceUri(null)).isEqualTo(URI.create("/"));

        ProblemDetail withoutRequest = ProblemDetailFactory.build(ApiErrorCode.INTERNAL_ERROR, "err", null);
        assertThat(withoutRequest.getInstance()).isEqualTo(URI.create("/"));

        assertThat(ProblemDetailFactory.sanitizePropertyName(null)).isEqualTo("unknown");
        assertThat(ProblemDetailFactory.sanitizePropertyName("   ")).isEqualTo("unknown");
        assertThat(ProblemDetailFactory.sanitizePropertyName("role\r\nName")).isEqualTo("role_Name");
        assertThat(ProblemDetailFactory.sanitizePropertyName("a".repeat(80))).hasSize(64);
    }

    @Test
    void shouldMapHandlerMethodValidationExceptionWithAndWithoutErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts/v2/tenants");

        MethodParameter namedParam = Mockito.mock(MethodParameter.class);
        Mockito.when(namedParam.getParameterName()).thenReturn("tenantId");
        MessageSourceResolvable namedMsg = Mockito.mock(MessageSourceResolvable.class);
        Mockito.when(namedMsg.getDefaultMessage()).thenReturn("must match ^[a-z][a-z0-9_]{1,49}$");
        ParameterValidationResult namedResult =
                new ParameterValidationResult(namedParam, "BAD", List.of(namedMsg), null, null, null);

        MethodParameter unnamedParam = Mockito.mock(MethodParameter.class);
        Mockito.when(unnamedParam.getParameterName()).thenReturn(null);
        MessageSourceResolvable nullMsg = Mockito.mock(MessageSourceResolvable.class);
        Mockito.when(nullMsg.getDefaultMessage()).thenReturn(null);
        ParameterValidationResult unnamedResult =
                new ParameterValidationResult(unnamedParam, -1, List.of(nullMsg), null, null, null);

        ParameterValidationResult nullMethodParamResult = Mockito.mock(ParameterValidationResult.class);
        Mockito.when(nullMethodParamResult.getMethodParameter()).thenReturn(null);
        Mockito.when(nullMethodParamResult.getResolvableErrors()).thenReturn(List.of(nullMsg));

        HandlerMethodValidationException populatedEx = Mockito.mock(HandlerMethodValidationException.class);
        Mockito.when(populatedEx.getAllValidationResults())
                .thenReturn(List.of(namedResult, unnamedResult, nullMethodParamResult));

        ResponseEntity<ProblemDetail> populatedResp = handler.handleHandlerMethodValidation(populatedEx, request);
        assertThat(populatedResp.getStatusCode().value()).isEqualTo(400);
        assertThat(populatedResp.getBody()).isNotNull();
        assertThat(populatedResp.getBody().getProperties()).containsEntry("errorCode", "VALIDATION_FAILED");
        @SuppressWarnings("unchecked")
        var fieldErrors = (java.util.Map<String, String>) populatedResp.getBody().getProperties().get("fieldErrors");
        assertThat(fieldErrors)
                .containsEntry("tenantId", "must match ^[a-z][a-z0-9_]{1,49}$")
                .containsEntry("parameter", "Invalid value");

        HandlerMethodValidationException emptyEx = Mockito.mock(HandlerMethodValidationException.class);
        Mockito.when(emptyEx.getAllValidationResults()).thenReturn(List.of());

        ResponseEntity<ProblemDetail> emptyResp = handler.handleHandlerMethodValidation(emptyEx, request);
        assertThat(emptyResp.getStatusCode().value()).isEqualTo(400);
        assertThat(emptyResp.getBody()).isNotNull();
        assertThat(emptyResp.getBody().getProperties()).doesNotContainKey("fieldErrors");

        // Also cover 405 when supportedMethods is null or empty
        ResponseEntity<ProblemDetail> noAllowNull = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"), request);
        assertThat(noAllowNull.getStatusCode().value()).isEqualTo(405);
        assertThat(noAllowNull.getHeaders().getAllow()).isEmpty();

        HttpRequestMethodNotSupportedException emptyAllowedEx =
                new HttpRequestMethodNotSupportedException("PATCH", List.of());
        ResponseEntity<ProblemDetail> noAllowEmpty = handler.handleMethodNotSupported(emptyAllowedEx, request);
        assertThat(noAllowEmpty.getStatusCode().value()).isEqualTo(405);
        assertThat(noAllowEmpty.getHeaders().getAllow()).isEmpty();
    }

    @Test
    void shouldHandleGlobalObjectErrorsNullMessagesAndPopulatedConstraintViolations() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/validate");

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payload");
        bindingResult.addError(new FieldError("payload", "email", null));
        bindingResult.addError(new ObjectError("payload", "Cross-field check failed"));
        bindingResult.addError(new ObjectError("payloadNullMsg", null));

        MethodArgumentNotValidException validationEx = Mockito.mock(MethodArgumentNotValidException.class);
        Mockito.when(validationEx.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ProblemDetail> validationResp = handler.handleValidation(validationEx, request);
        assertThat(validationResp.getStatusCode().value()).isEqualTo(400);
        assertThat(validationResp.getBody()).isNotNull();
        assertThat(validationResp.getBody().getProperties()).containsKey("fieldErrors");

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> populatedViolation = Mockito.mock(ConstraintViolation.class);
        Path mockPath = Mockito.mock(Path.class);
        Mockito.when(mockPath.toString()).thenReturn("tenantId");
        Mockito.when(populatedViolation.getPropertyPath()).thenReturn(mockPath);
        Mockito.when(populatedViolation.getMessage()).thenReturn("must match regex");

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> nullFieldsViolation = Mockito.mock(ConstraintViolation.class);
        Mockito.when(nullFieldsViolation.getPropertyPath()).thenReturn(null);
        Mockito.when(nullFieldsViolation.getMessage()).thenReturn(null);

        ResponseEntity<ProblemDetail> cvResp = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(populatedViolation, nullFieldsViolation)), request);
        assertThat(cvResp.getStatusCode().value()).isEqualTo(400);
        assertThat(cvResp.getBody()).isNotNull();
        assertThat(cvResp.getBody().getProperties()).containsKey("fieldErrors");
    }

    @Test
    void apiExceptionShouldRejectNullErrorCodeImmediately() {
        assertThatThrownBy(() -> new ApiException(null, "msg"))
                .isInstanceOf(NullPointerException.class);
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
    record NestedPayload(String label) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record StrictPayload(
            @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[a-zA-Z0-9 ]+$") String displayName,
            @NotBlank String code,
            @Valid NestedPayload nested
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

        @GetMapping("/test/param")
        int param(@RequestParam int size) {
            return size;
        }

        @GetMapping("/test/missing-header")
        String missingHeader(@RequestHeader("X-Required") String header) {
            return header;
        }

        @GetMapping("/test/missing-part")
        void missingPart() throws MissingServletRequestPartException {
            throw new MissingServletRequestPartException("file");
        }

        @GetMapping(value = "/test/strict-dto-json", produces = MediaType.APPLICATION_JSON_VALUE)
        StrictPayload strictDtoJson() {
            return new StrictPayload("ok", "c1", null);
        }

        @GetMapping("/test/constraint-violation")
        void constraintViolation() {
            throw new ConstraintViolationException("Invalid constraint", Set.of());
        }

        @GetMapping("/test/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing");
        }

        @GetMapping("/test/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(5_242_880L);
        }

        @GetMapping("/test/auth-error")
        void authError() {
            throw new BadCredentialsException("Bad credentials");
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
