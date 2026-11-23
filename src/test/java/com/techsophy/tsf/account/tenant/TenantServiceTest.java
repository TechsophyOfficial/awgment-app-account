package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.keycloak.KeycloakRealmClient;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractVerifier;
import com.techsophy.tsf.account.security.TenantAuthenticationManagerResolver;
import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.tenant.dto.TenantResponse;
import com.techsophy.tsf.account.tenant.dto.UpdateTenantStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private TenantRepository tenantRepository;
    private TenantStateManager tenantStateManager;
    private TenantFlywayMigrator tenantFlywayMigrator;
    private TenantSeeder tenantSeeder;
    private KeycloakRealmClient keycloakRealmClient;
    private KeycloakRealmContractVerifier contractVerifier;
    private TenantLookupService tenantLookupService;
    private TenantAuthenticationManagerResolver authManagerResolver;
    private SecurityAuditService securityAuditService;
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        tenantStateManager = mock(TenantStateManager.class);
        tenantFlywayMigrator = mock(TenantFlywayMigrator.class);
        tenantSeeder = mock(TenantSeeder.class);
        keycloakRealmClient = mock(KeycloakRealmClient.class);
        contractVerifier = mock(KeycloakRealmContractVerifier.class);
        tenantLookupService = mock(TenantLookupService.class);
        authManagerResolver = mock(TenantAuthenticationManagerResolver.class);
        securityAuditService = mock(SecurityAuditService.class);

        tenantService = new TenantService(
                tenantRepository,
                tenantStateManager,
                tenantFlywayMigrator,
                tenantSeeder,
                keycloakRealmClient,
                contractVerifier,
                tenantLookupService,
                authManagerResolver,
                securityAuditService
        );
    }

    @Test
    void createTenantShouldProvisionMigrateSeedVerifyAndActivateWithAuditLog() {
        String tenantId50 = "a" + "b".repeat(49);
        CreateTenantRequest request = new CreateTenantRequest(
                tenantId50,
                "Fifty Char Tenant",
                "admin" + "@" + "acme.invalid",
                "Acme",
                "Approver"
        );
        TenantEntity provisioning = entity(tenantId50, TenantStatus.PROVISIONING, 0L);
        TenantEntity active = entity(tenantId50, TenantStatus.ACTIVE, 1L);
        KeycloakRealmContractSnapshot snapshot = minimalSnapshot(tenantId50);

        when(tenantStateManager.beginProvisioning(request, "platform-admin-sub")).thenReturn(provisioning);
        when(keycloakRealmClient.fetchRealmContract(tenantId50)).thenReturn(snapshot);
        when(tenantStateManager.markActive(tenantId50, "platform-admin-sub")).thenReturn(active);

        TenantResponse response = tenantService.createTenant(request, "platform-admin-sub");

        assertThat(response.tenantId()).isEqualTo(tenantId50);
        assertThat(response.schemaName()).isEqualTo("tenant_" + tenantId50);
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.keycloakContractVerified()).isTrue();
        assertThat(response.version()).isEqualTo(1L);

        verify(tenantFlywayMigrator).provisionAndMigrateTenantSchema(tenantId50);
        verify(tenantSeeder).seedTenant(tenantId50, "admin" + "@" + "acme.invalid", "Acme", "Approver");
        verify(contractVerifier).verify(snapshot);
        verify(tenantLookupService).evict(tenantId50);
        verify(securityAuditService).record(
                eq("TENANT_PROVISION"), eq("TENANT"), eq(tenantId50),
                eq(AuditOutcome.SUCCESS), eq("platform-admin-sub"), isNull(), anyMap()
        );
    }

    @Test
    void createTenantShouldReject51CharOrReservedTenantIdBeforeAnySideEffect() {
        String tenantId51 = "a" + "b".repeat(50);
        for (String invalid : List.of(tenantId51, "public", "platform", "pg_internal", "Acme")) {
            assertThatThrownBy(() -> tenantService.createTenant(
                    new CreateTenantRequest(invalid, "Bad", null, null, null), "actor"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.INVALID_TENANT_ID));
        }
        verifyNoInteractions(tenantStateManager, tenantFlywayMigrator, tenantSeeder, keycloakRealmClient);
    }

    @Test
    void createTenantShouldPersistProvisioningFailedAndTruncateLongErrorWhenKeycloakFails() {
        CreateTenantRequest request = new CreateTenantRequest("acme", "Acme Corp", null, null, null);
        when(tenantStateManager.beginProvisioning(request, "actor"))
                .thenReturn(entity("acme", TenantStatus.PROVISIONING, 0L));
        String longDetail = "Violation: " + "x".repeat(2500);
        when(keycloakRealmClient.fetchRealmContract("acme"))
                .thenThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, longDetail));

        assertThatThrownBy(() -> tenantService.createTenant(request, "actor"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        verify(tenantStateManager).markProvisioningFailed(
                eq("acme"),
                argThat(msg -> msg.length() == 2000),
                eq("actor")
        );
        verify(tenantLookupService).evict("acme");
        verify(securityAuditService).record(
                eq("TENANT_PROVISION"), eq("TENANT"), eq("acme"),
                eq(AuditOutcome.FAILURE), eq("actor"), isNull(), anyMap()
        );
    }

    @Test
    void retryProvisioningShouldRecoverFailedTenantAndRecordFailureOnRepeatedError() {
        when(tenantStateManager.beginRetryProvisioning("acme", "actor"))
                .thenReturn(entity("acme", TenantStatus.PROVISIONING, 1L));
        KeycloakRealmContractSnapshot snapshot = minimalSnapshot("acme");
        when(keycloakRealmClient.fetchRealmContract("acme")).thenReturn(snapshot);
        when(tenantStateManager.markActive("acme", "actor"))
                .thenReturn(entity("acme", TenantStatus.ACTIVE, 2L));

        TenantResponse recovered = tenantService.retryProvisioning("acme", "actor");

        assertThat(recovered.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(recovered.keycloakContractVerified()).isTrue();
        verify(tenantFlywayMigrator).provisionAndMigrateTenantSchema("acme");
        verify(tenantSeeder).seedTenant("acme", null, null, null);
        verify(contractVerifier).verify(snapshot);

        // Failure path on retry marks PROVISIONING_FAILED again
        doThrow(new IllegalStateException("flyway deadlock"))
                .when(tenantFlywayMigrator).provisionAndMigrateTenantSchema("acme");
        assertThatThrownBy(() -> tenantService.retryProvisioning("acme", "actor"))
                .isInstanceOf(IllegalStateException.class);
        verify(tenantStateManager).markProvisioningFailed("acme", "flyway deadlock", "actor");
    }

    @Test
    void listTenantsShouldSupportOptionalStatusFilterSortingAndRejectInvalidBoundsOrSort() {
        TenantEntity acme = entity("acme", TenantStatus.ACTIVE, 1L);
        when(tenantRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(acme)));
        when(tenantRepository.findByStatus(eq(TenantStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(acme)));

        PageResponse<TenantResponse> all = tenantService.listTenants(null, 0, 20, "createdAt,desc");
        assertThat(all.content()).hasSize(1);
        assertThat(all.totalElements()).isEqualTo(1L);

        PageResponse<TenantResponse> filtered = tenantService.listTenants(TenantStatus.ACTIVE, 0, 10, "tenantId");
        assertThat(filtered.content()).hasSize(1);
        verify(tenantRepository).findByStatus(
                eq(TenantStatus.ACTIVE),
                argThat(p -> p.getSort().getOrderFor("tenantId").getDirection() == Sort.Direction.ASC)
        );

        for (int badPage : List.of(-1, -5)) {
            assertThatThrownBy(() -> tenantService.listTenants(null, badPage, 20, "createdAt,desc"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
        for (int badSize : List.of(0, -1, 101, 1000)) {
            assertThatThrownBy(() -> tenantService.listTenants(null, 0, badSize, "createdAt,desc"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
        for (String badSort : List.of("", "optLockVersion,desc", "createdAt,sideways", "tenantId,asc,extra")) {
            assertThatThrownBy(() -> tenantService.listTenants(null, 0, 20, badSort))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
    }

    @Test
    void updateTenantStatusShouldSuspendAndEvictCachesAndDisableKeycloakRealm() {
        TenantEntity active = entity("acme", TenantStatus.ACTIVE, 1L);
        TenantEntity suspended = entity("acme", TenantStatus.SUSPENDED, 2L);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(active));
        when(tenantStateManager.updateStatus("acme", TenantStatus.SUSPENDED, 1L, "actor"))
                .thenReturn(suspended);

        TenantResponse response = tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "Security maintenance", 1L),
                "actor"
        );

        assertThat(response.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(response.version()).isEqualTo(2L);
        verify(tenantLookupService).evict("acme");
        verify(authManagerResolver).evictTenant("acme");
        verify(keycloakRealmClient).setRealmEnabled("acme", false);
        verify(securityAuditService).record(
                eq("TENANT_SUSPEND"), eq("TENANT"), eq("acme"),
                eq(AuditOutcome.SUCCESS), eq("actor"), isNull(), anyMap()
        );
    }

    @Test
    void updateTenantStatusShouldMigrateEnableVerifyOnReactivateAndRollbackRealmOnContractFailure() {
        TenantEntity suspended = entity("acme", TenantStatus.SUSPENDED, 2L);
        TenantEntity reactivated = entity("acme", TenantStatus.ACTIVE, 3L);
        KeycloakRealmContractSnapshot snapshot = minimalSnapshot("acme");

        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(suspended));
        when(keycloakRealmClient.fetchRealmContract("acme")).thenReturn(snapshot);
        when(tenantStateManager.updateStatus("acme", TenantStatus.ACTIVE, 2L, "actor"))
                .thenReturn(reactivated);

        TenantResponse response = tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.ACTIVE, "Done", 2L),
                "actor"
        );

        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.keycloakContractVerified()).isTrue();
        verify(tenantFlywayMigrator).provisionAndMigrateTenantSchema("acme");
        verify(keycloakRealmClient).setRealmEnabled("acme", true);
        verify(contractVerifier).verify(snapshot);
        verify(tenantStateManager).updateStatus("acme", TenantStatus.ACTIVE, 2L, "actor");

        // Failure on verifier must re-disable realm best-effort and keep tenant SUSPENDED
        org.mockito.Mockito.clearInvocations(tenantStateManager, keycloakRealmClient, securityAuditService);
        doThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "policy violated"))
                .when(contractVerifier).verify(snapshot);
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.ACTIVE, "Retry", 2L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        verify(keycloakRealmClient).setRealmEnabled("acme", false);
        verify(tenantStateManager, never()).updateStatus("acme", TenantStatus.ACTIVE, 2L, "actor");
        verify(securityAuditService).record(
                eq("TENANT_REACTIVATE"), eq("TENANT"), eq("acme"),
                eq(AuditOutcome.FAILURE), eq("actor"), isNull(), anyMap()
        );

        // Even if rollback setRealmEnabled(false) and verifier throw non-ApiException, surface UPSTREAM_IDENTITY_PROVIDER_ERROR
        doThrow(new IllegalStateException("kc timeout")).when(contractVerifier).verify(snapshot);
        doThrow(new IllegalStateException("kc disable failed")).when(keycloakRealmClient).setRealmEnabled("acme", false);
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.ACTIVE, "Retry2", 2L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));
    }

    @Test
    void updateTenantStatusSuspendFailureShouldAuditFailureAndWrapNonApiException() {
        TenantEntity active = entity("acme", TenantStatus.ACTIVE, 1L);
        TenantEntity suspended = entity("acme", TenantStatus.SUSPENDED, 2L);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(active));
        when(tenantStateManager.updateStatus("acme", TenantStatus.SUSPENDED, 1L, "actor"))
                .thenReturn(suspended);
        doThrow(new IllegalStateException("kc down")).when(keycloakRealmClient).setRealmEnabled("acme", false);

        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "Incident", 1L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR));

        verify(securityAuditService).record(
                eq("TENANT_SUSPEND"), eq("TENANT"), eq("acme"),
                eq(AuditOutcome.FAILURE), eq("actor"), isNull(), anyMap()
        );
    }

    @Test
    void updateTenantStatusShouldEnforce404VersionConflictAndValidStateTransitions() {
        when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "missing",
                new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "x", 0L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        TenantEntity active = entity("acme", TenantStatus.ACTIVE, 5L);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "x", 4L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));

        // Same state ACTIVE -> ACTIVE rejected
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.ACTIVE, "x", 5L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION));

        // Target PROVISIONING / PROVISIONING_FAILED rejected
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "acme",
                new UpdateTenantStatusRequest(TenantStatus.PROVISIONING, "x", 5L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION));

        // Transitioning out of PROVISIONING_FAILED rejected
        TenantEntity failed = entity("failed_t", TenantStatus.PROVISIONING_FAILED, 1L);
        when(tenantRepository.findByTenantId("failed_t")).thenReturn(Optional.of(failed));
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "failed_t",
                new UpdateTenantStatusRequest(TenantStatus.ACTIVE, "x", 1L),
                "actor"
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.INVALID_STATE_TRANSITION));

        // Null sort, null error message on provisioning failure, suspend ApiException rethrow, and PageResponse null content
        assertThatThrownBy(() -> tenantService.listTenants(null, 0, 20, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        CreateTenantRequest failReq = new CreateTenantRequest("fail_null", "Fail", null, null, null);
        when(tenantStateManager.beginProvisioning(failReq, "actor"))
                .thenReturn(entity("fail_null", TenantStatus.PROVISIONING, 0L));
        doThrow(new RuntimeException((String) null)).when(tenantFlywayMigrator).provisionAndMigrateTenantSchema("fail_null");
        assertThatThrownBy(() -> tenantService.createTenant(failReq, "actor"))
                .isInstanceOf(RuntimeException.class);
        verify(tenantStateManager).markProvisioningFailed("fail_null", "Provisioning failed", "actor");

        TenantEntity activeForSuspend = entity("suspend_api_err", TenantStatus.ACTIVE, 1L);
        when(tenantRepository.findByTenantId("suspend_api_err")).thenReturn(Optional.of(activeForSuspend));
        when(tenantStateManager.updateStatus("suspend_api_err", TenantStatus.SUSPENDED, 1L, "actor"))
                .thenReturn(entity("suspend_api_err", TenantStatus.SUSPENDED, 2L));
        doThrow(new ApiException(ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR, "kc down"))
                .when(keycloakRealmClient).setRealmEnabled("suspend_api_err", false);
        assertThatThrownBy(() -> tenantService.updateTenantStatus(
                "suspend_api_err",
                new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "x", 1L),
                "actor"
        )).isInstanceOf(ApiException.class);

        PageResponse<TenantResponse> emptyPage = new PageResponse<>(null, 0, 20, 0L, 0);
        assertThat(emptyPage.content()).isEmpty();
    }

    private static TenantEntity entity(String tenantId, TenantStatus status, long version) {
        TenantEntity entity = new TenantEntity();
        entity.setId(100L);
        entity.setTenantId(tenantId);
        entity.setSchemaName("tenant_" + tenantId);
        entity.setDisplayName(tenantId + " Org");
        entity.setStatus(status);
        entity.setOptLockVersion(version);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static KeycloakRealmContractSnapshot minimalSnapshot(String realm) {
        return new KeycloakRealmContractSnapshot(
                realm, true, true, false, false, true, "en", 300,
                Set.of(), List.of(), Map.of(), null, List.of()
        );
    }
}
