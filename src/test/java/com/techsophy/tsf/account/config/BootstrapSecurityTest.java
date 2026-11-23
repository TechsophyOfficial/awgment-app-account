package com.techsophy.tsf.account.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void springBootObjectMapperShouldFailOnUnknownJsonPropertiesGlobally() {
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .as("Global BOPLA protection requires spring.jackson.deserialization.fail-on-unknown-properties=true")
                .isTrue();
    }

    @Test
    void shouldPermitUnauthenticatedGetAndHeadLivenessAndReadinessProbesWithoutDetails() throws Exception {
        for (String path : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(header().doesNotExist("Set-Cookie"));

            mockMvc.perform(head(path))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void shouldRejectNonReadVerbsOnPublicHealthProbesUnauthenticated() throws Exception {
        mockMvc.perform(post("/actuator/health/liveness"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void shouldBlockRootActuatorHealthAndSensitiveEndpointsUnauthenticated() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/env", "/actuator/beans", "/actuator/configprops"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                    .andExpect(jsonPath("$.instance").value(path))
                    .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404ForDisabledSensitiveActuatorEndpointsEvenWhenAuthenticated() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/configprops"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Test
    void shouldBlockSwaggerAndOpenApiDocsWhenDevDocsDisabledByDefault() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        }
    }

    @Test
    void shouldReturnRfc7807ProblemDetailAndWwwAuthenticateForUnauthenticatedApiCalls() throws Exception {
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.type").value("https://awgment.dev/errors/invalid-token"))
                .andExpect(jsonPath("$.title").value("Authentication Required"))
                .andExpect(jsonPath("$.instance").value("/accounts/v2/users/me"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void accessDeniedHandlerShouldEmitRfc7807ForbiddenProblemDetail() throws Exception {
        BootstrapSecurityConfig config = new BootstrapSecurityConfig();
        AccessDeniedHandler handler = config.problemDetailAccessDeniedHandler(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/accounts/v2/tenants/acme");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.handle(req, res, new AccessDeniedException("Forbidden"));

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(res.getContentAsString()).contains("\"errorCode\":\"ACCESS_DENIED\"");
    }
}
