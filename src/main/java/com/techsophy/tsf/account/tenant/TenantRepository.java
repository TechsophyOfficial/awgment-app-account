package com.techsophy.tsf.account.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TenantEntity} records in {@code public.tp_tenant}.
 */
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    List<TenantEntity> findByStatusOrderByTenantIdAsc(TenantStatus status);

    Page<TenantEntity> findByStatus(TenantStatus status, Pageable pageable);
}
