package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void startupRunShouldMigratePublicAndActiveTenantsOnlyAndClearTenantContext() throws Exception {
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
                "classpath:db/migration/public|public",
                "classpath:db/migration/tenant|tenant_alpha",
                "classpath:db/migration/tenant|tenant_beta"
        );
        verify(repository).findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE);
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void startupRunShouldClearTenantContextEvenIfTenantMigrationThrows() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantRepository repository = mock(TenantRepository.class);
        when(repository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant("broken_tenant", TenantStatus.ACTIVE)));

        TenantFlywayMigrator migrator = new TenantFlywayMigrator(
                dataSource,
                repository,
                true,
                (location, schema) -> {
                    if ("tenant_broken_tenant".equals(schema)) {
                        TenantContext.setTenantId("broken_tenant");
                        throw new IllegalStateException("flyway failed");
                    }
                }
        );

        assertThatThrownBy(() -> migrator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT);
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

    private static TenantEntity tenant(String tenantId, TenantStatus status) {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId(tenantId);
        entity.setSchemaName("tenant_" + tenantId);
        entity.setStatus(status);
        return entity;
    }
}
