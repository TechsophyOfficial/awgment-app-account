package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Executes schema-per-tenant Flyway migrations on startup (for {@code ACTIVE} tenants only)
 * and on demand during tenant provisioning / reactivation (UC-TENANT-01..04).
 * Uses PostgreSQL {@code pg_advisory_xact_lock} for multi-replica DDL serialization and
 * isolates startup failures per tenant so a single broken schema never crashes the cluster.
 */
@Slf4j
@Component
public class TenantFlywayMigrator implements ApplicationRunner {

    private static final String TENANT_MIGRATION_LOCATION = "classpath:db/migration/tenant";
    private static final int ADVISORY_LOCK_NAMESPACE = 7429;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final String SQLSTATE_UNIQUE_VIOLATION = "235" + "05";
    private static final String SQLSTATE_DUPLICATE_SCHEMA = "42P06";

    private final DataSource dataSource;
    private final TenantRepository tenantRepository;
    private final TenantStateManager tenantStateManager;
    private final TenantLookupService tenantLookupService;
    private final SecurityAuditService securityAuditService;
    private final boolean migrateOnStartup;
    private final FlywayRunner flywayRunner;

    @Autowired
    public TenantFlywayMigrator(
            DataSource dataSource,
            TenantRepository tenantRepository,
            TenantStateManager tenantStateManager,
            TenantLookupService tenantLookupService,
            SecurityAuditService securityAuditService,
            @Value("${awgment.flyway.migrate-on-startup:true}") boolean migrateOnStartup) {
        this(
                dataSource,
                tenantRepository,
                tenantStateManager,
                tenantLookupService,
                securityAuditService,
                migrateOnStartup,
                defaultFlywayRunner(dataSource)
        );
    }

    public TenantFlywayMigrator(
            DataSource dataSource,
            TenantRepository tenantRepository,
            boolean migrateOnStartup) {
        this(dataSource, tenantRepository, null, null, null, migrateOnStartup, defaultFlywayRunner(dataSource));
    }

    TenantFlywayMigrator(
            DataSource dataSource,
            TenantRepository tenantRepository,
            boolean migrateOnStartup,
            FlywayRunner flywayRunner) {
        this(dataSource, tenantRepository, null, null, null, migrateOnStartup, flywayRunner);
    }

    TenantFlywayMigrator(
            DataSource dataSource,
            TenantRepository tenantRepository,
            TenantStateManager tenantStateManager,
            TenantLookupService tenantLookupService,
            SecurityAuditService securityAuditService,
            boolean migrateOnStartup,
            FlywayRunner flywayRunner) {
        this.dataSource = dataSource;
        this.tenantRepository = tenantRepository;
        this.tenantStateManager = tenantStateManager;
        this.tenantLookupService = tenantLookupService;
        this.securityAuditService = securityAuditService;
        this.migrateOnStartup = migrateOnStartup;
        this.flywayRunner = flywayRunner;
    }

    private static FlywayRunner defaultFlywayRunner(DataSource dataSource) {
        return (location, schema) ->
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations(location)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .baselineOnMigrate(false)
                        .load()
                        .migrate();
    }

    public void provisionAndMigrateTenantSchema(String tenantId) {
        String schemaName = TenantIdValidator.toSchemaName(tenantId);
        Connection primaryConn;
        try {
            primaryConn = dataSource.getConnection();
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to create schema for tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "'", ex);
        }

        try {
            if (isPostgreSql(primaryConn)) {
                runUnderPostgresAdvisoryLock(primaryConn, tenantId, schemaName);
            } else {
                try (primaryConn) {
                    createSchemaIfNotExists(primaryConn, tenantId, schemaName);
                }
                flywayRunner.migrate(TENANT_MIGRATION_LOCATION, schemaName);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to create schema for tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "'", ex);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrateOnStartup) {
            return;
        }
        try {
            TenantContext.clear();
            List<TenantEntity> activeTenants = tenantRepository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE);
            for (TenantEntity tenant : activeTenants) {
                String tenantId = tenant.getTenantId();
                try {
                    TenantContext.clear();
                    provisionAndMigrateTenantSchema(tenantId);
                } catch (RuntimeException ex) {
                    log.error("Startup Flyway migration failed for active tenant '{}'; quarantining as PROVISIONING_FAILED",
                            ProblemDetailFactory.sanitizeForLog(tenantId), ex);
                    quarantineFailedTenant(tenantId, ex);
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void runUnderPostgresAdvisoryLock(
            Connection lockConn, String tenantId, String schemaName) throws SQLException {
        boolean originalAutoCommit = lockConn.getAutoCommit();
        try {
            if (originalAutoCommit) {
                lockConn.setAutoCommit(false);
            }
            try (PreparedStatement lockPs = lockConn.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, hashtext(?))")) {
                lockPs.setInt(1, ADVISORY_LOCK_NAMESPACE);
                lockPs.setString(2, schemaName);
                lockPs.execute();
            }
            try (Connection ddlConn = dataSource.getConnection()) {
                createSchemaIfNotExists(ddlConn, tenantId, schemaName);
            }
            flywayRunner.migrate(TENANT_MIGRATION_LOCATION, schemaName);
            lockConn.commit();
        } catch (SQLException | RuntimeException ex) {
            try {
                lockConn.rollback();
            } catch (SQLException rollbackEx) {
                ex.addSuppressed(rollbackEx);
            }
            throw ex;
        } finally {
            try {
                if (originalAutoCommit && !lockConn.isClosed()) {
                    lockConn.setAutoCommit(true);
                }
            } catch (SQLException resetEx) {
                log.warn("Failed to restore autoCommit after advisory lock for schema {}", schemaName, resetEx);
            } finally {
                try {
                    lockConn.close();
                } catch (SQLException closeEx) {
                    log.warn("Failed to close advisory lock connection for schema {}", schemaName, closeEx);
                }
            }
        }
    }

    private static void createSchemaIfNotExists(
            Connection connection, String tenantId, String schemaName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        } catch (SQLException ex) {
            String sqlState = ex.getSQLState();
            if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState) || SQLSTATE_DUPLICATE_SCHEMA.equals(sqlState)) {
                log.debug("Schema '{}' already created concurrently (sqlState={})", schemaName, sqlState);
                return;
            }
            throw new IllegalStateException(
                    "Failed to create schema for tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "'", ex);
        }
    }

    private static boolean isPostgreSql(Connection connection) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            return meta != null && meta.getDatabaseProductName() != null
                    && meta.getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }

    private void quarantineFailedTenant(String tenantId, RuntimeException ex) {
        TenantContext.clear();
        String safeError = truncateError(ex.getMessage());
        try {
            if (tenantStateManager != null) {
                tenantStateManager.quarantineActiveTenant(tenantId, safeError, "system");
            } else {
                tenantRepository.findByTenantId(tenantId).ifPresent(entity -> {
                    if (entity.getStatus() == TenantStatus.ACTIVE) {
                        entity.setStatus(TenantStatus.PROVISIONING_FAILED);
                        entity.setLastError(safeError);
                        entity.setUpdatedBy("system");
                        tenantRepository.saveAndFlush(entity);
                    }
                });
            }
        } catch (RuntimeException quarantineEx) {
            log.error("Failed to persist PROVISIONING_FAILED quarantine for tenant '{}'",
                    ProblemDetailFactory.sanitizeForLog(tenantId), quarantineEx);
        }
        if (tenantLookupService != null) {
            tenantLookupService.evict(tenantId);
        }
        if (securityAuditService != null) {
            try {
                securityAuditService.recordIsolated(
                        "TENANT_STARTUP_MIGRATION",
                        "TENANT",
                        tenantId,
                        AuditOutcome.FAILURE,
                        "system",
                        null,
                        Map.of("error", ex.getClass().getSimpleName())
                );
            } catch (RuntimeException auditEx) {
                log.error("Failed to record startup migration failure audit for tenant '{}'",
                        ProblemDetailFactory.sanitizeForLog(tenantId), auditEx);
            }
        }
    }

    private static String truncateError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Startup migration failed";
        }
        String collapsed = raw.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", "_").trim();
        return collapsed.length() > MAX_ERROR_LENGTH ? collapsed.substring(0, MAX_ERROR_LENGTH) : collapsed;
    }

    @FunctionalInterface
    interface FlywayRunner {
        void migrate(String location, String schemaName);
    }
}
