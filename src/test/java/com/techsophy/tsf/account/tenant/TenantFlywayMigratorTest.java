package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantFlywayMigratorTest {

    @AfterEach
    void cleanupContext() {
        TenantContext.clear();
    }

    @Test
    void provisionAndMigrateShouldRejectInvalidOrReservedTenantIdBeforeTouchingDataSource() {
        DataSource dataSource = mock(DataSource.class);
        TenantRepository repository = mock(TenantRepository.class);
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource, repository, true, (loc, schema) -> { }
        );

        for (String badId : List.of("public", "platform", "pg_catalog", "BadId", "a" + "b".repeat(50))) {
            assertThatThrownBy(() -> migrator.provisionAndMigrateTenantSchema(badId))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.INVALID_TENANT_ID));
        }
        verifyNoInteractions(dataSource, repository);
    }

    @Test
    void provisionAndMigrateShouldCreateQuotedSchemaAndRunTenantFlyway() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        List<String> migrations = new ArrayList<>();
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource,
                mock(TenantRepository.class),
                true,
                (location, schema) -> migrations.add(location + "|" + schema)
        );

        migrator.provisionAndMigrateTenantSchema("acme");

        verify(statement).execute("CREATE SCHEMA IF NOT EXISTS \"tenant_acme\"");
        verify(statement).close();
        verify(connection).close();
        assertThat(migrations).containsExactly("classpath:db/migration/tenant|tenant_acme");
    }

    @Test
    void provisionAndMigrateShouldWrapSqlFailureCleanly() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("permission denied"));

        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource,
                mock(TenantRepository.class),
                true,
                (location, schema) -> { }
        );

        assertThatThrownBy(() -> migrator.provisionAndMigrateTenantSchema("acme"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acme");
    }

    @Test
    void startupRunShouldSkipWhenDisabled() {
        DataSource dataSource = mock(DataSource.class);
        TenantRepository repository = mock(TenantRepository.class);
        List<String> migrations = new ArrayList<>();
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource, repository, false, (loc, schema) -> migrations.add(loc + "|" + schema)
        );

        migrator.run(new DefaultApplicationArguments());

        assertThat(migrations).isEmpty();
        verifyNoInteractions(dataSource, repository);
    }

    @Test
    void provisionAndMigrateShouldAcquirePostgresAdvisoryLockAndHandleConcurrentSchemaSqlState() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection lockConn = mock(Connection.class);
        Connection ddlConn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        PreparedStatement lockPs = mock(PreparedStatement.class);
        Statement ddlStmt = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(lockConn, ddlConn);
        when(lockConn.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(lockConn.getAutoCommit()).thenReturn(true);
        when(lockConn.prepareStatement(contains("pg_advisory_xact_lock"))).thenReturn(lockPs);
        when(ddlConn.createStatement()).thenReturn(ddlStmt);
        when(ddlStmt.execute("CREATE SCHEMA IF NOT EXISTS \"tenant_acme\""))
                .thenThrow(new SQLException("duplicate key value violates unique constraint", "235" + "05"));

        List<String> migrations = new ArrayList<>();
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource,
                mock(TenantRepository.class),
                null,
                null,
                null,
                true,
                (location, schema) -> migrations.add(location + "|" + schema)
        );

        assertThatCode(() -> migrator.provisionAndMigrateTenantSchema("acme")).doesNotThrowAnyException();

        var order = inOrder(lockConn, lockPs, ddlStmt);
        order.verify(lockConn).setAutoCommit(false);
        order.verify(lockPs).setInt(1, 7429);
        order.verify(lockPs).setString(2, "tenant_acme");
        order.verify(lockPs).execute();
        order.verify(ddlStmt).execute("CREATE SCHEMA IF NOT EXISTS \"tenant_acme\"");
        order.verify(lockConn).commit();
        order.verify(lockConn).setAutoCommit(true);
        order.verify(lockConn).close();
        assertThat(migrations).containsExactly("classpath:db/migration/tenant|tenant_acme");
    }

    @Test
    void startupRunShouldMigrateActiveTenantsWithoutRedundantPublicMigrationAndClearTenantContext() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantEntity alpha = tenant("alpha", TenantStatus.ACTIVE);
        TenantEntity beta = tenant("beta", TenantStatus.ACTIVE);
        TenantRepository repository = mock(TenantRepository.class);
        when(repository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE))
                .thenReturn(List.of(alpha, beta));

        List<String> migrations = new ArrayList<>();
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource,
                repository,
                true,
                (location, schema) -> migrations.add(location + "|" + schema)
        );

        TenantContext.setTenantId("alpha");
        migrator.run(new DefaultApplicationArguments());

        assertThat(migrations).containsExactly(
                "classpath:db/migration/tenant|tenant_alpha",
                "classpath:db/migration/tenant|tenant_beta"
        );
        verify(repository).findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE);
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void startupRunShouldIsolateBrokenActiveTenantQuarantineAndContinueRemainingTenants() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantRepository repository = mock(TenantRepository.class);
        TenantStateManager stateManager = mock(TenantStateManager.class);
        TenantLookupService lookupService = mock(TenantLookupService.class);
        SecurityAuditService auditService = mock(SecurityAuditService.class);
        when(repository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE))
                .thenReturn(List.of(
                        tenant("broken_tenant", TenantStatus.ACTIVE),
                        tenant("healthy_tenant", TenantStatus.ACTIVE)
                ));

        List<String> migrated = new ArrayList<>();
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource,
                repository,
                stateManager,
                lookupService,
                auditService,
                true,
                (location, schema) -> {
                    if ("tenant_broken_tenant".equals(schema)) {
                        TenantContext.setTenantId("broken_tenant");
                        throw new IllegalStateException("flyway checksum mismatch");
                    }
                    migrated.add(schema);
                }
        );

        assertThatCode(() -> migrator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        assertThat(migrated).containsExactly("tenant_healthy_tenant");
        verify(stateManager).quarantineActiveTenant(
                eq("broken_tenant"),
                contains("flyway checksum mismatch"),
                eq("system")
        );
        verify(lookupService).evict("broken_tenant");
        verify(auditService).recordIsolated(
                eq("TENANT_STARTUP_MIGRATION"),
                eq("TENANT"),
                eq("broken_tenant"),
                eq(AuditOutcome.FAILURE),
                eq("system"),
                isNull(),
                anyMap()
        );
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);

        // Audit failure during quarantine must also never crash startup
        doThrow(new RuntimeException("audit table error"))
                .when(auditService)
                .recordIsolated(anyString(), anyString(), anyString(), any(), anyString(), isNull(), anyMap());
        assertThatCode(() -> migrator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void autowiredConstructorShouldConfigureRealFlywayRunner() throws Exception {
        DataSource h2 = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:flyway_ctor_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        TenantFlywayMigrator migrator = new TenantFlywayMigrator(h2, mock(TenantRepository.class), true);
        // Exercises the real Flyway.configure()...load().migrate() lambda (fails on PG-specific DDL in H2)
        assertThatThrownBy(() -> migrator.provisionAndMigrateTenantSchema("acme"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void advisoryLockAndQuarantineFallbackShouldHandleRollbackSchemaConflictsAndEdgeErrors() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection lockConn = mock(Connection.class);
        Connection ddlConn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        PreparedStatement lockPs = mock(PreparedStatement.class);
        Statement ddlStmt = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(lockConn, ddlConn, lockConn, ddlConn, lockConn);
        when(lockConn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(lockConn.getAutoCommit()).thenReturn(false);
        when(lockConn.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))")).thenReturn(lockPs);
        when(ddlConn.createStatement()).thenReturn(ddlStmt);

        // First run: SQLSTATE duplicate_schema ("42P" + "06") is tolerated and Flyway migration succeeds
        when(ddlStmt.execute(contains("CREATE SCHEMA IF NOT EXISTS")))
                .thenThrow(new SQLException("schema exists", "42P" + "06"))
                .thenThrow(new SQLException("permission denied", "425" + "01"));
        List<String> migrated = new ArrayList<>();
        TenantRepository repo = mock(TenantRepository.class);
        TenantFlywayMigrator threeArg = new TenantFlywayMigrator(
                dataSource, repo, null, null, null, true, (loc, sch) -> migrated.add(sch));
        assertThatCode(() -> threeArg.provisionAndMigrateTenantSchema("acme")).doesNotThrowAnyException();
        assertThat(migrated).containsExactly("tenant_acme");
        verify(lockConn).commit();

        // Second run: non-duplicate SQLException triggers lock rollback + quarantine via repo fallback + suppressed/close resilience
        when(lockConn.getAutoCommit()).thenReturn(true);
        when(lockConn.isClosed()).thenReturn(true);
        doThrow(new SQLException("rollback failed")).when(lockConn).rollback();
        doThrow(new SQLException("close failed")).when(lockConn).close();

        TenantEntity activeTenant = tenant("acme", TenantStatus.ACTIVE);
        TenantEntity nonActiveTenant = tenant("beta", TenantStatus.SUSPENDED);
        when(repo.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE)).thenReturn(List.of(activeTenant, nonActiveTenant));
        when(repo.findByTenantId("acme")).thenReturn(Optional.of(activeTenant));
        when(repo.findByTenantId("beta")).thenReturn(Optional.of(nonActiveTenant));
        doThrow(new SQLException("meta failed")).when(meta).getDatabaseProductName();

        // Simulate metadata throwing -> non-PG branch failing DDL with long multiline error; beta non-ACTIVE skipped by repo fallback
        assertThatCode(() -> threeArg.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThat(activeTenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
        assertThat(activeTenant.getLastError()).isNotBlank();
        assertThat(nonActiveTenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);

        // Long (>1000 char) and blank exception messages in quarantine truncation
        TenantStateManager failingQuarantine = mock(TenantStateManager.class);
        doThrow(new RuntimeException("quarantine db down")).when(failingQuarantine)
                .quarantineActiveTenant(anyString(), anyString(), anyString());
        TenantFlywayMigrator withStateMgr = new TenantFlywayMigrator(
                dataSource, repo, failingQuarantine, null, null, true,
                (loc, sch) -> {
                    throw new RuntimeException((sch.equals("tenant_blank")) ? "   " : ("line1\n" + "x".repeat(600 * 2)));
                });
        when(lockConn.getMetaData()).thenThrow(new SQLException("meta unavailable"));
        when(repo.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE)).thenReturn(List.of(
                tenant("blank", TenantStatus.ACTIVE),
                tenant("longerr", TenantStatus.ACTIVE)));
        when(dataSource.getConnection()).thenReturn(lockConn, lockConn)
                .thenThrow(new SQLException("pool exhausted"));
        assertThatCode(() -> withStateMgr.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThatThrownBy(() -> withStateMgr.provisionAndMigrateTenantSchema("acme"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create schema");
    }

    private static TenantEntity tenant(String tenantId, TenantStatus status) {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId(tenantId);
        entity.setSchemaName("tenant_" + tenantId);
        entity.setStatus(status);
        return entity;
    }
}
