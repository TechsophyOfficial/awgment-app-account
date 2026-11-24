package com.techsophy.tsf.account.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link UserEntity} records in {@code tp_user}.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByKeycloakSub(String keycloakSub);

    Optional<UserEntity> findByUserNameIgnoreCase(String userName);

    Optional<UserEntity> findByEmailIdIgnoreCase(String emailId);

    @Query("""
            SELECT (COUNT(u) > 0) FROM UserEntity u
            WHERE LOWER(u.userName) = LOWER(:userName)
              AND u.status IN (com.techsophy.tsf.account.user.UserStatus.ACTIVE, com.techsophy.tsf.account.user.UserStatus.DISABLED)
            """)
    boolean existsActiveOrDisabledByUserName(@Param("userName") String userName);

    @Query("""
            SELECT (COUNT(u) > 0) FROM UserEntity u
            WHERE LOWER(u.userName) = LOWER(:userName)
              AND u.id <> :excludeId
              AND u.status IN (com.techsophy.tsf.account.user.UserStatus.ACTIVE, com.techsophy.tsf.account.user.UserStatus.DISABLED)
            """)
    boolean existsActiveOrDisabledByUserNameAndIdNot(
            @Param("userName") String userName,
            @Param("excludeId") Long excludeId);

    @Query("""
            SELECT (COUNT(u) > 0) FROM UserEntity u
            WHERE LOWER(u.emailId) = LOWER(:emailId)
              AND u.status IN (com.techsophy.tsf.account.user.UserStatus.ACTIVE, com.techsophy.tsf.account.user.UserStatus.DISABLED)
            """)
    boolean existsActiveOrDisabledByEmail(@Param("emailId") String emailId);

    @Query("""
            SELECT (COUNT(u) > 0) FROM UserEntity u
            WHERE LOWER(u.emailId) = LOWER(:emailId)
              AND u.id <> :excludeId
              AND u.status IN (com.techsophy.tsf.account.user.UserStatus.ACTIVE, com.techsophy.tsf.account.user.UserStatus.DISABLED)
            """)
    boolean existsActiveOrDisabledByEmailAndIdNot(
            @Param("emailId") String emailId,
            @Param("excludeId") Long excludeId);

    @Transactional
    default int updateSyncProgress(
            Long id,
            String keycloakSub,
            long syncedVersion,
            SyncStatus syncStatus,
            Instant updatedAt) {
        Objects.requireNonNull(syncStatus, "syncStatus must not be null");
        if (syncStatus == SyncStatus.SYNCED) {
            return markSyncSucceeded(id, keycloakSub, syncedVersion, updatedAt);
        }
        return markSyncStatus(id, keycloakSub, syncedVersion, syncStatus, updatedAt);
    }

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserEntity u
            SET u.keycloakSub = COALESCE(:keycloakSub, u.keycloakSub),
                u.syncedVersion = CASE WHEN :syncedVersion > u.syncedVersion THEN :syncedVersion ELSE u.syncedVersion END,
                u.syncStatus = CASE
                    WHEN u.syncVersion <= :syncedVersion THEN com.techsophy.tsf.account.user.SyncStatus.SYNCED
                    ELSE u.syncStatus
                END,
                u.updatedAt = :updatedAt
            WHERE u.id = :id
            """)
    int markSyncSucceeded(
            @Param("id") Long id,
            @Param("keycloakSub") String keycloakSub,
            @Param("syncedVersion") long syncedVersion,
            @Param("updatedAt") Instant updatedAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserEntity u
            SET u.keycloakSub = COALESCE(:keycloakSub, u.keycloakSub),
                u.syncedVersion = CASE WHEN :syncedVersion > u.syncedVersion THEN :syncedVersion ELSE u.syncedVersion END,
                u.syncStatus = :syncStatus,
                u.updatedAt = :updatedAt
            WHERE u.id = :id
            """)
    int markSyncStatus(
            @Param("id") Long id,
            @Param("keycloakSub") String keycloakSub,
            @Param("syncedVersion") long syncedVersion,
            @Param("syncStatus") SyncStatus syncStatus,
            @Param("updatedAt") Instant updatedAt);
}
