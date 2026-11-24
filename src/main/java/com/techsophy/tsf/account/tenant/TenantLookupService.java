package com.techsophy.tsf.account.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, bounded-TTL tenant status cache backed by {@link TenantRepository}.
 * Missing tenants are never cached as active, and entries expire within <= 60 seconds.
 */
@Service
public class TenantLookupService {

    private static final long MAX_TTL_SECONDS = 60L;

    private final TenantRepository tenantRepository;
    private final Duration cacheTtl;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedEntry> statusCache = new ConcurrentHashMap<>();

    @Autowired
    public TenantLookupService(
            TenantRepository tenantRepository,
            @Value("${awgment.multitenancy.status-cache-ttl-seconds:60}") long ttlSeconds) {
        this(tenantRepository, ttlSeconds, Clock.systemUTC());
    }

    TenantLookupService(TenantRepository tenantRepository, long ttlSeconds, Clock clock) {
        this.tenantRepository = tenantRepository;
        long boundedSeconds = Math.min(Math.max(ttlSeconds, 0L), MAX_TTL_SECONDS);
        this.cacheTtl = Duration.ofSeconds(boundedSeconds);
        this.clock = clock;
    }

    public Optional<TenantStatus> findStatus(String tenantId) {
        if (!TenantIdValidator.isValid(tenantId)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (!cacheTtl.isZero()) {
            CachedEntry cached = statusCache.get(tenantId);
            if (cached != null && now.isBefore(cached.expiresAt())) {
                return Optional.of(cached.status());
            }
            if (cached != null) {
                statusCache.remove(tenantId, cached);
            }
        }

        Optional<TenantStatus> fetched = tenantRepository.findByTenantId(tenantId)
                .map(TenantEntity::getStatus);
        if (fetched.isPresent() && !cacheTtl.isZero()) {
            statusCache.put(tenantId, new CachedEntry(fetched.get(), now.plus(cacheTtl)));
        } else {
            statusCache.remove(tenantId);
        }
        return fetched;
    }

    public void evict(String tenantId) {
        if (tenantId != null) {
            statusCache.remove(tenantId);
        }
    }

    public void evictAll() {
        statusCache.clear();
    }

    private record CachedEntry(TenantStatus status, Instant expiresAt) {
    }
}
