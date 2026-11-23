package com.techsophy.tsf.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "awgment.security.dev-docs-enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapSecurityDevDocsEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPermitSwaggerHtmlUiAndOpenApiDocsWithUsableCspWhileKeepingStrictApiSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Awgment Headless IAM API"))
                .andExpect(jsonPath("$.info.version").value("v2.3"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("default-src 'none'"))))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));

        // API routes must still keep strict default-src 'none' CSP and require auth
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }
}
