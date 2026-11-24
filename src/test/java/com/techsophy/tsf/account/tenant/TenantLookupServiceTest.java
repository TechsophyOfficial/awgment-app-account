package com.techsophy.tsf.account.tenant;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantLookupServiceTest {

    @Test
    void shouldCacheStatusWithinTtlAndRefreshAfterExpiry() {
        TenantRepository repository = mock(TenantRepository.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
        TenantLookupService service = new TenantLookupService(repository, 60, clock);

        TenantEntity active = tenant("acme", TenantStatus.ACTIVE);
        TenantEntity suspended = tenant("acme", TenantStatus.SUSPENDED);
        when(repository.findByTenantId("acme"))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.of(suspended));

        // First call -> DB hit
        assertThat(service.findStatus("acme")).contains(TenantStatus.ACTIVE);
        // Within TTL (59s later) -> cache hit
        clock.advanceSeconds(59);
        assertThat(service.findStatus("acme")).contains(TenantStatus.ACTIVE);
        verify(repository, times(1)).findByTenantId("acme");

        // At TTL boundary (60s total elapsed) -> expired -> DB hit
        clock.advanceSeconds(1);
        assertThat(service.findStatus("acme")).contains(TenantStatus.SUSPENDED);
        verify(repository, times(2)).findByTenantId("acme");
    }

    @Test
    void shouldNotCacheMissingTenantsSoNewlyProvisionedTenantIsVisibleImmediately() {
        TenantRepository repository = mock(TenantRepository.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
        TenantLookupService service = new TenantLookupService(repository, 60, clock);

        when(repository.findByTenantId("beta"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(tenant("beta", TenantStatus.ACTIVE)));

        assertThat(service.findStatus("beta")).isEmpty();
        assertThat(service.findStatus("beta")).contains(TenantStatus.ACTIVE);
        verify(repository, times(2)).findByTenantId("beta");
    }

    @Test
    void shouldEvictSingleTenantAndAllTenantsExplicitly() {
        TenantRepository repository = mock(TenantRepository.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
        TenantLookupService service = new TenantLookupService(repository, 60, clock);

        when(repository.findByTenantId("alpha"))
                .thenReturn(Optional.of(tenant("alpha", TenantStatus.ACTIVE)))
                .thenReturn(Optional.of(tenant("alpha", TenantStatus.SUSPENDED)));
        when(repository.findByTenantId("beta"))
                .thenReturn(Optional.of(tenant("beta", TenantStatus.ACTIVE)))
                .thenReturn(Optional.of(tenant("beta", TenantStatus.PROVISIONING_FAILED)));

        assertThat(service.findStatus("alpha")).contains(TenantStatus.ACTIVE);
        assertThat(service.findStatus("beta")).contains(TenantStatus.ACTIVE);

        service.evict("alpha");
        service.evict(null);

        // alpha evicted -> re-queries DB; beta remains cached
        assertThat(service.findStatus("alpha")).contains(TenantStatus.SUSPENDED);
        assertThat(service.findStatus("beta")).contains(TenantStatus.ACTIVE);
        verify(repository, times(2)).findByTenantId("alpha");
        verify(repository, times(1)).findByTenantId("beta");

        service.evictAll();
        assertThat(service.findStatus("beta")).contains(TenantStatus.PROVISIONING_FAILED);
        verify(repository, times(2)).findByTenantId("beta");
    }

    @Test
    void shouldClampTtlAbove60SecondsAndBypassCacheWhenTtlIsZeroOrNegative() {
        TenantRepository repository = mock(TenantRepository.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));

        // TTL > 60s must be clamped to 60s
        TenantLookupService clamped = new TenantLookupService(repository, 300, clock);
        when(repository.findByTenantId("acme"))
                .thenReturn(Optional.of(tenant("acme", TenantStatus.ACTIVE)))
                .thenReturn(Optional.of(tenant("acme", TenantStatus.SUSPENDED)));

        assertThat(clamped.findStatus("acme")).contains(TenantStatus.ACTIVE);
        clock.advanceSeconds(60);
        assertThat(clamped.findStatus("acme")).contains(TenantStatus.SUSPENDED);
        verify(repository, times(2)).findByTenantId("acme");

        // TTL <= 0 bypasses cache
        TenantRepository repo2 = mock(TenantRepository.class);
        TenantLookupService uncached = new TenantLookupService(repo2, 0, clock);
        when(repo2.findByTenantId("acme"))
                .thenReturn(Optional.of(tenant("acme", TenantStatus.PROVISIONING)))
                .thenReturn(Optional.of(tenant("acme", TenantStatus.ACTIVE)));
        assertThat(uncached.findStatus("acme")).contains(TenantStatus.PROVISIONING);
        assertThat(uncached.findStatus("acme")).contains(TenantStatus.ACTIVE);
        verify(repo2, times(2)).findByTenantId("acme");
    }

    @Test
    void shouldReturnEmptyWithoutDbQueryForInvalidOrReservedTenantIdAndRemoveStaleEntryWhenDeleted() {
        TenantRepository repository = mock(TenantRepository.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
        TenantLookupService service = new TenantLookupService(repository, 60, clock);

        assertThat(service.findStatus(null)).isEmpty();
        assertThat(service.findStatus("public")).isEmpty();
        assertThat(service.findStatus("Invalid")).isEmpty();
        verifyNoInteractions(repository);

        when(repository.findByTenantId("temp"))
                .thenReturn(Optional.of(tenant("temp", TenantStatus.PROVISIONING)))
                .thenReturn(Optional.empty());
        assertThat(service.findStatus("temp")).contains(TenantStatus.PROVISIONING);
        clock.advanceSeconds(61);
        assertThat(service.findStatus("temp")).isEmpty();

        // Public constructor using system UTC clock
        TenantRepository sysRepo = mock(TenantRepository.class);
        when(sysRepo.findByTenantId("acme")).thenReturn(Optional.of(tenant("acme", TenantStatus.ACTIVE)));
        TenantLookupService sysService = new TenantLookupService(sysRepo, 60L);
        assertThat(sysService.findStatus("acme")).contains(TenantStatus.ACTIVE);
    }

    private static TenantEntity tenant(String tenantId, TenantStatus status) {
        TenantEntity entity = new TenantEntity();
        entity.setId(1001L);
        entity.setTenantId(tenantId);
        entity.setSchemaName("tenant_" + tenantId);
        entity.setDisplayName(tenantId);
        entity.setStatus(status);
        return entity;
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advanceSeconds(long seconds) {
            current.updateAndGet(now -> now.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
