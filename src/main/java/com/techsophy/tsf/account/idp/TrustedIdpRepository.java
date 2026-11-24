package com.techsophy.tsf.account.idp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Tenant-scoped repository for {@link TrustedIdpEntity} records in {@code tp_trusted_idp}.
 */
public interface TrustedIdpRepository extends JpaRepository<TrustedIdpEntity, Long> {

    Optional<TrustedIdpEntity> findByIdpAlias(String idpAlias);

    boolean existsByIdpAliasAndEnabledTrue(String idpAlias);
}
