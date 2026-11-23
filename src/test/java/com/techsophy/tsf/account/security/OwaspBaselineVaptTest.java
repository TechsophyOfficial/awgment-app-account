package com.techsophy.tsf.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.GlobalExceptionHandler;
import com.techsophy.tsf.account.support.TestJwtFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 OWASP API Security Top 10 (2023) baseline VAPT suite covering:
 * API2 (JWT alg:none & payload tampering), API4 (deeply nested JSON DoS),
 * API5 (HTTP TRACE method tampering), API8 (strict security headers & StrictHttpFirewall),
 * and API9 (decommissioned v1 shadow route non-exposure).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwaspBaselineVaptTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void api8_shouldEmitStrictOwaspSecurityHeadersWithoutServerBannersOnPublicAndProtectedRoutes() throws Exception {
        for (String path : new String[]{"/actuator/health/liveness", "/accounts/v2/users/me"}) {
            mockMvc.perform(get(path))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
                    .andExpect(header().doesNotExist("X-Powered-By"))
                    .andExpect(header().doesNotExist("Server"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Test
    void api5_shouldBlockHttpTraceMethodTamperingAndNeverEchoAuthorizationHeader() throws Exception {
        for (String path : new String[]{"/actuator/health/liveness", "/accounts/v2/users/me"}) {
            MvcResult result = mockMvc.perform(request(HttpMethod.TRACE, path)
                            .header("Authorization", "Bearer vapt-canary-token"))
                    .andReturn();
            int code = result.getResponse().getStatus();
            assertThat(code)
                    .as("TRACE on %s must be rejected, not 200", path)
                    .isIn(400, 401, 403, 405);
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("vapt-canary-token");
        }
    }

    @Test
    void api8_strictHttpFirewallShouldRejectSemicolonEncodedSlashDoubleSlashAndNullByteTraversal() throws Exception {
        String[] maliciousPaths = new String[]{
                "/actuator/health/liveness;jsessionid=evil",
                "/actuator/health/liveness/..%2fenv",
                "/accounts/v2/users%00",
                "/accounts/v2//users"
        };

        for (String malicious : maliciousPaths) {
            try {
                MvcResult result = mockMvc.perform(get(malicious)).andReturn();
                assertThat(result.getResponse().getStatus())
                        .as("Malicious path %s must be blocked with 400/401/403", malicious)
                        .isIn(400, 401, 403);
            } catch (RequestRejectedException expected) {
                // Spring Security StrictHttpFirewall rejected the malformed path before servlet dispatch
                assertThat(expected.getMessage()).isNotBlank();
            }
        }
    }

    @Test
    void api9_decommissionedV1ShadowEndpointsMustNeverBeExposed() throws Exception {
        String[] retiredV1Paths = new String[]{
                "/accounts/v1/users",
                "/internal/v1/users/create",
                "/accounts/v1/otp/generate",
                "/accounts/v1/menus",
                "/accounts/v1/themes"
        };

        for (String path : retiredV1Paths) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        }
    }

    @Test
    void api2_shouldRejectAlgNoneTamperedPayloadAndMalformedJwtAttacks() {
        TestJwtFactory factory = new TestJwtFactory();

        // 1. Unsigned alg:none JWT attack
        String noneHeader = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String escalatedPayload = base64Url(
                "{\"iss\":\"http://localhost:8080/realms/platform\",\"sub\":\"attacker\",\"aud\":[\"awgment-account\"]}");
        String algNoneToken = noneHeader + "." + escalatedPayload + ".";
        assertThatThrownBy(() -> factory.decode(algNoneToken))
                .isInstanceOf(JwtException.class);

        // 2. Tampered payload on a legitimately signed RS256 token
        String validToken = factory.mintToken(TestJwtFactory.TokenSpec.activeHuman(
                "acme", "sub-1", "normal-user", List.of("awgment-user")));
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + escalatedPayload + "." + parts[2];
        assertThatThrownBy(() -> factory.decode(tamperedToken))
                .isInstanceOf(JwtException.class);

        // 3. Garbage non-JWT token
        assertThatThrownBy(() -> factory.decode("not.a.valid.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void api4_shouldRejectDeeplyNestedJsonDosPayloadCleanlyWith400Not500() throws Exception {
        ObjectMapper strictMapper = Jackson2ObjectMapperBuilder.json()
                .failOnUnknownProperties(true)
                .build();
        MockMvc standaloneMvc = MockMvcBuilders.standaloneSetup(new JsonProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(strictMapper))
                .build();

        // 1,500 nested JSON objects exceed Jackson's default max nesting depth (1,000)
        int depth = 1500;
        StringBuilder nestedJson = new StringBuilder(depth * 7);
        for (int i = 0; i < depth; i++) {
            nestedJson.append("{\"a\":");
        }
        nestedJson.append("1");
        for (int i = 0; i < depth; i++) {
            nestedJson.append('}');
        }

        standaloneMvc.perform(post("/vapt/json-probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nestedJson.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    record ProbeBody(@NotBlank String name) {
    }

    @RestController
    static class JsonProbeController {
        @PostMapping("/vapt/json-probe")
        String probe(@Valid @RequestBody ProbeBody body) {
            return body.name();
        }
    }
}
