package com.techsophy.tsf.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void shouldPermitUnauthenticatedLivenessProbeWithoutExposingDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldPermitUnauthenticatedReadinessProbeWithoutExposingDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldBlockRootActuatorHealthAndSensitiveEndpointsUnauthenticated() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/env", "/actuator/beans", "/actuator/configprops"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                    .andExpect(jsonPath("$.instance").value(path))
                    .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Test
    void shouldBlockSwaggerAndOpenApiDocsWhenDevDocsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void shouldReturnRfc7807ProblemDetailAndNoSessionCookieForUnauthenticatedApiCalls() throws Exception {
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.type").value("https://awgment.dev/errors/invalid-token"))
                .andExpect(jsonPath("$.title").value("Authentication Required"))
                .andExpect(jsonPath("$.instance").value("/accounts/v2/users/me"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
