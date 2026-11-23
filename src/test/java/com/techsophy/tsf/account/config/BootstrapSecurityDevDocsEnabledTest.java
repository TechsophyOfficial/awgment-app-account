package com.techsophy.tsf.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "awgment.security.dev-docs-enabled=true",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapSecurityDevDocsEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPermitOpenApiDocsWhenExplicitlyEnabledWhileKeepingApiEndpointsProtected() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Awgment Headless IAM API"))
                .andExpect(jsonPath("$.info.version").value("v2.3"));

        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }
}
