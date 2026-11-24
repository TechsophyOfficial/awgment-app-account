package com.techsophy.tsf.account.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.GlobalExceptionHandler;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.outbox.dto.OutboxEventResponse;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncOutboxControllerTest {

    private SyncOutboxService syncOutboxService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        syncOutboxService = mock(SyncOutboxService.class);
        ObjectMapper strictMapper = Jackson2ObjectMapperBuilder.json()
                .failOnUnknownProperties(true)
                .build();
        strictMapper.findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new SyncOutboxController(syncOutboxService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(strictMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void listOutboxShouldReturnPaginatedEventsAndMapValidationErrors() throws Exception {
        OutboxEventResponse eventResp = new OutboxEventResponse(
                9001L,
                OutboxJobType.RECONCILE_USER,
                OutboxAggregateType.USER,
                501L,
                2L,
                false,
                OutboxStatus.FAILED,
                false,
                5,
                Instant.now(),
                null,
                null,
                "KC 503",
                Instant.now(),
                Instant.now()
        );
        when(syncOutboxService.listOutboxEvents(OutboxStatus.FAILED, OutboxAggregateType.USER, 0, 50))
                .thenReturn(new PageResponse<>(List.of(eventResp), 0, 50, 1L, 1));

        mockMvc.perform(get("/accounts/v2/sync/outbox")
                        .param("status", "FAILED")
                        .param("aggregateType", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9001))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].leaseToken").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));

        when(syncOutboxService.listOutboxEvents(null, null, -1, 50))
                .thenThrow(new ApiException(ApiErrorCode.VALIDATION_FAILED, "Page index must be >= 0."));
        mockMvc.perform(get("/accounts/v2/sync/outbox").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void retryOutboxShouldReturn200ResolveActorFallbacksAndPropagate404And409() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject("operator-sub-1")
                .claim("iss", "http://localhost:8080/realms/acme")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        OutboxEventResponse retried = new OutboxEventResponse(
                9001L, OutboxJobType.RECONCILE_USER, OutboxAggregateType.USER, 501L,
                2L, false, OutboxStatus.COMPLETED, false, 0, Instant.now(),
                null, null, null, Instant.now(), Instant.now()
        );
        when(syncOutboxService.retryOutboxEvent(9001L, "operator-sub-1")).thenReturn(retried);

        mockMvc.perform(post("/accounts/v2/sync/outbox/9001/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9001))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Non-JWT authentication falls back to auth name, empty context falls back to system
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fallback-op", "n/a"));
        when(syncOutboxService.retryOutboxEvent(9002L, "fallback-op")).thenReturn(retried);
        mockMvc.perform(post("/accounts/v2/sync/outbox/9002/retry"))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        when(syncOutboxService.retryOutboxEvent(9003L, "system")).thenReturn(retried);
        mockMvc.perform(post("/accounts/v2/sync/outbox/9003/retry"))
                .andExpect(status().isOk());

        Jwt blankSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject(" ")
                .claim("iss", "http://localhost:8080/realms/acme")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(blankSubJwt, List.of(), " "));
        mockMvc.perform(post("/accounts/v2/sync/outbox/9003/retry"))
                .andExpect(status().isOk());

        Jwt nullSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", "http://localhost:8080/realms/acme")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(nullSubJwt, List.of()));
        mockMvc.perform(post("/accounts/v2/sync/outbox/9003/retry"))
                .andExpect(status().isOk());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(null, "n/a"));
        mockMvc.perform(post("/accounts/v2/sync/outbox/9003/retry"))
                .andExpect(status().isOk());

        when(syncOutboxService.retryOutboxEvent(404L, "system"))
                .thenThrow(new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "Not found"));
        mockMvc.perform(post("/accounts/v2/sync/outbox/404/retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        when(syncOutboxService.retryOutboxEvent(409L, "system"))
                .thenThrow(new ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "Ambiguous email"));
        mockMvc.perform(post("/accounts/v2/sync/outbox/409/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void syncOutboxServiceShouldEnforceFiltersRetryGuardsAuditAndDispatch() {
        KeycloakOutboxRepository outboxRepo = mock(KeycloakOutboxRepository.class);
        OutboxStore outboxStore = mock(OutboxStore.class);
        OutboxService outboxService = mock(OutboxService.class);
        SecurityAuditService auditService = mock(SecurityAuditService.class);

        SyncOutboxService service = new SyncOutboxService(
                outboxRepo, outboxStore, outboxService, auditService);

        KeycloakOutboxEventEntity failedReconcile = new KeycloakOutboxEventEntity();
        failedReconcile.setId(801L);
        failedReconcile.setJobType(OutboxJobType.RECONCILE_USER);
        failedReconcile.setAggregateType(OutboxAggregateType.USER);
        failedReconcile.setAggregateId(501L);
        failedReconcile.setTargetVersion(2L);
        failedReconcile.setStatus(OutboxStatus.FAILED);
        failedReconcile.setRetryCount(5);
        failedReconcile.setLastError("Upstream error");
        failedReconcile.setLeaseToken(UUID.randomUUID());
        failedReconcile.setCreatedOn(Instant.now());
        failedReconcile.setUpdatedOn(Instant.now());

        when(outboxRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedReconcile), PageRequest.of(0, 50), 1));
        when(outboxRepo.findByStatus(eq(OutboxStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedReconcile), PageRequest.of(0, 50), 1));
        when(outboxRepo.findByAggregateType(eq(OutboxAggregateType.USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedReconcile), PageRequest.of(0, 50), 1));
        when(outboxRepo.findByStatusAndAggregateType(
                eq(OutboxStatus.FAILED), eq(OutboxAggregateType.USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedReconcile), PageRequest.of(0, 50), 1));

        assertThat(service.listOutboxEvents(null, null, 0, 50).content()).hasSize(1);
        assertThat(service.listOutboxEvents(OutboxStatus.FAILED, null, 0, 50).content()).hasSize(1);
        assertThat(service.listOutboxEvents(null, OutboxAggregateType.USER, 0, 50).content()).hasSize(1);
        assertThat(service.listOutboxEvents(OutboxStatus.FAILED, OutboxAggregateType.USER, 0, 50).content())
                .hasSize(1);

        assertThatThrownBy(() -> service.listOutboxEvents(null, null, -1, 50))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.listOutboxEvents(null, null, 0, 101))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.listOutboxEvents(null, null, 0, 0))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        when(outboxStore.resetFailedForRetry(404L))
                .thenThrow(new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "missing"));
        assertThatThrownBy(() -> service.retryOutboxEvent(404L, "op-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        when(outboxStore.resetFailedForRetry(802L))
                .thenThrow(new ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "ambiguous"));
        assertThatThrownBy(() -> service.retryOutboxEvent(802L, "op-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION));

        KeycloakOutboxEventEntity resetEntity = new KeycloakOutboxEventEntity();
        resetEntity.setId(801L);
        resetEntity.setJobType(OutboxJobType.RECONCILE_USER);
        resetEntity.setAggregateType(OutboxAggregateType.USER);
        resetEntity.setAggregateId(501L);
        resetEntity.setStatus(OutboxStatus.PENDING);
        resetEntity.setRetryCount(0);
        resetEntity.setCreatedOn(Instant.now());
        resetEntity.setUpdatedOn(Instant.now());

        when(outboxStore.resetFailedForRetry(801L)).thenReturn(resetEntity);
        when(outboxRepo.findById(801L)).thenReturn(Optional.of(resetEntity));
        TenantContext.setTenantId("acme");

        OutboxEventResponse response = service.retryOutboxEvent(801L, "op-1");
        assertThat(response.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(response.retryCount()).isZero();
        verify(auditService).record(
                eq("OUTBOX_RETRY"),
                eq("OUTBOX_EVENT"),
                eq("801"),
                eq(AuditOutcome.SUCCESS),
                eq("op-1"),
                isNull(),
                anyMap()
        );
        verify(outboxService).dispatchPendingBatch("acme", 1);

        TenantContext.clear();
        OutboxEventResponse defaultTenantResp = service.retryOutboxEvent(801L, "op-2");
        assertThat(defaultTenantResp.status()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxService, org.mockito.Mockito.times(1)).dispatchPendingBatch(any(), any(Integer.class));
    }
}
