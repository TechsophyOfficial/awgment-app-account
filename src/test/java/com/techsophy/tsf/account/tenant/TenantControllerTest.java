package com.techsophy.tsf.account.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.GlobalExceptionHandler;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.tenant.dto.TenantResponse;
import com.techsophy.tsf.account.tenant.dto.UpdateTenantStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantControllerTest {

    private TenantService tenantService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        tenantService = mock(TenantService.class);
        ObjectMapper strictMapper = Jackson2ObjectMapperBuilder.json()
                .failOnUnknownProperties(true)
                .build();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TenantController(tenantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(strictMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void createTenantShouldReturn201WithLocationAndResponseBody() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject("platform-admin-1")
                .claim("iss", "http://localhost:8080/realms/platform")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("awgment-platform-admin"))));

        TenantResponse expected = new TenantResponse(
                "acme", "tenant_acme", "Acme Corporation", TenantStatus.ACTIVE,
                true, null, 1L, Instant.now(), Instant.now()
        );
        when(tenantService.createTenant(any(CreateTenantRequest.class), eq("platform-admin-1")))
                .thenReturn(expected);

        mockMvc.perform(post("/accounts/v2/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "acme",
                                  "displayName": "Acme Corporation",
                                  "bootstrapAdminEmail": "",
                                  "bootstrapAdminFirstName": "Acme",
                                  "bootstrapAdminLastName": "Approver"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/v2/tenants/acme"))
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.schemaName").value("tenant_acme"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.keycloakContractVerified").value(true))
                .andExpect(jsonPath("$.lastError").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void createTenantShouldRejectInvalidDisplayNameOrMalformedEmailWith400ValidationFailed() throws Exception {
        mockMvc.perform(post("/accounts/v2/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme\",\"displayName\":\" \",\"bootstrapAdminEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.bootstrapAdminEmail").exists());

        verifyNoInteractions(tenantService);
    }

    @Test
    void retryProvisioningShouldReturn200AndFallbackActorWhenPrincipalIsNotJwt() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mock-platform-admin", "n/a"));
        when(tenantService.retryProvisioning("acme", "mock-platform-admin"))
                .thenReturn(new TenantResponse(
                        "acme", "tenant_acme", "Acme", TenantStatus.ACTIVE,
                        true, null, 2L, Instant.now(), Instant.now()
                ));

        mockMvc.perform(post("/accounts/v2/tenants/acme/retry-provisioning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void listTenantsShouldPassQueryParametersToService() throws Exception {
        PageResponse<TenantResponse> page = new PageResponse<>(
                List.of(new TenantResponse(
                        "acme", "tenant_acme", "Acme", TenantStatus.ACTIVE,
                        true, null, 1L, Instant.now(), Instant.now()
                )),
                0, 20, 1L, 1
        );
        when(tenantService.listTenants(TenantStatus.ACTIVE, 0, 20, "createdAt,desc")).thenReturn(page);

        mockMvc.perform(get("/accounts/v2/tenants")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tenantId").value("acme"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updateTenantStatusShouldReturn200AndMapServiceConflicts() throws Exception {
        when(tenantService.updateTenantStatus(
                eq("acme"), any(UpdateTenantStatusRequest.class), eq("system")))
                .thenReturn(new TenantResponse(
                        "acme", "tenant_acme", "Acme", TenantStatus.SUSPENDED,
                        false, null, 2L, Instant.now(), Instant.now()
                ));

        mockMvc.perform(patch("/accounts/v2/tenants/acme/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Maintenance\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.version").value(2));

        verify(tenantService).updateTenantStatus(
                eq("acme"),
                eq(new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "Maintenance", 1L)),
                eq("system")
        );

        when(tenantService.updateTenantStatus(eq("acme"), any(UpdateTenantStatusRequest.class), any()))
                .thenThrow(new ApiException(ApiErrorCode.CONCURRENT_MODIFICATION, "stale version"));
        mockMvc.perform(patch("/accounts/v2/tenants/acme/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"Done\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));

        // Blank subject in JWT falls back to auth name or system cleanly
        Jwt blankSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject(" ")
                .claim("iss", "http://localhost:8080/realms/platform")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(blankSubJwt, List.of(), " "));
        mockMvc.perform(post("/accounts/v2/tenants/acme/retry-provisioning"))
                .andExpect(status().isOk());
        verify(tenantService).retryProvisioning("acme", "system");

        SecurityContextHolder.clearContext();
        mockMvc.perform(post("/accounts/v2/tenants/beta/retry-provisioning"))
                .andExpect(status().isOk());
        verify(tenantService).retryProvisioning("beta", "system");

        Jwt nullSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", "http://localhost:8080/realms/platform")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(nullSubJwt, List.of(), null) {
            @Override
            public String getName() {
                return null;
            }
        });
        mockMvc.perform(post("/accounts/v2/tenants/gamma/retry-provisioning"))
                .andExpect(status().isOk());
        verify(tenantService).retryProvisioning("gamma", "system");
    }
}
