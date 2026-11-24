package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.outbox.dto.OutboxEventResponse;
import com.techsophy.tsf.account.security.JwtDecoderFactory;
import com.techsophy.tsf.account.security.TenantAuthenticationManagerResolver;
import com.techsophy.tsf.account.support.TestJwtFactory;
import com.techsophy.tsf.account.tenant.TenantEntity;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantRepository;
import com.techsophy.tsf.account.tenant.TenantStatus;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncOutboxSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantAuthenticationManagerResolver resolver;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantLookupService tenantLookupService;

    @MockBean
    private JwtDecoderFactory jwtDecoderFactory;

    @MockBean
    private SyncOutboxService syncOutboxService;

    private TestJwtFactory jwtFactory;

    @BeforeEach
    void setUp() {
        resolver.evictAll();
        tenantLookupService.evictAll();
        tenantRepository.deleteAll();
        TenantContext.clear();

        jwtFactory = new TestJwtFactory();
        when(jwtDecoderFactory.create("http://localhost:8080/realms/acme", "awgment-account"))
                .thenReturn(jwtFactory.decoder());

        TenantEntity acme = new TenantEntity();
        acme.setId(1L);
        acme.setTenantId("acme");
        acme.setSchemaName("tenant_acme");
        acme.setDisplayName("Acme");
        acme.setStatus(TenantStatus.ACTIVE);
        tenantRepository.saveAndFlush(acme);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        tenantLookupService.evictAll();
        tenantRepository.deleteAll();
    }

    @Test
    void shouldEnforcePreAuthorizeAuthoritiesAndBeanValidationOnSyncOutboxEndpoints() throws Exception {
        String readToken = tokenWithRoles(List.of("awgment-account-read"));
        String updateToken = tokenWithRoles(List.of("awgment-account-create-or-update"));
        String adminToken = tokenWithRoles(List.of("awgment-account-admin"));
        String allToken = tokenWithRoles(List.of("awgment-account-all"));
        String userToken = tokenWithRoles(List.of("awgment-user"));

        when(syncOutboxService.listOutboxEvents(any(), any(), eq(0), eq(50)))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0L, 0));
        when(syncOutboxService.retryOutboxEvent(eq(99L), any()))
                .thenReturn(new OutboxEventResponse(
                        99L, OutboxJobType.RECONCILE_USER, OutboxAggregateType.USER, 1L,
                        1L, false, OutboxStatus.PENDING, false, 0, Instant.now(),
                        null, null, null, Instant.now(), Instant.now()
                ));

        mockMvc.perform(get("/accounts/v2/sync/outbox"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Spring MVC @Min/@Max validation on request params
        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .header("Authorization", "Bearer " + readToken)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .header("Authorization", "Bearer " + readToken)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .header("Authorization", "Bearer " + readToken)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Retry RBAC
        mockMvc.perform(post("/accounts/v2/sync/outbox/99/retry")
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/accounts/v2/sync/outbox/99/retry")
                        .header("Authorization", "Bearer " + updateToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/accounts/v2/sync/outbox/99/retry")
                        .header("Authorization", "Bearer " + allToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/accounts/v2/sync/outbox/99/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        verify(syncOutboxService, times(2)).listOutboxEvents(any(), any(), eq(0), eq(50));
        verify(syncOutboxService, times(3)).retryOutboxEvent(eq(99L), any());
    }

    private String tokenWithRoles(List<String> roles) {
        return jwtFactory.mintToken(TestJwtFactory.TokenSpec.activeHuman("acme", "sub-1", "op", roles));
    }
}
