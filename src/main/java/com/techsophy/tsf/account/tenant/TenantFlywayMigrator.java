package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Executes schema-per-tenant Flyway migrations on startup (for {@code ACTIVE} tenants only)
 * and on demand during tenant provisioning / reactivation (UC-TENANT-01..04).
 */
@Component
public class TenantFlywayMigrator implements ApplicationRunner {

    private static final String PUBLIC_MIGRATION_LOCATION = "classpath:db/migration/public";
    private static final String TENANT_MIGRATION_LOCATION = "classpath:db/migration/tenant";

    private final DataSource dataSource;
    private final TenantRepository tenantRepository;
    private final boolean migrateOnStartup;
    private final FlywayRunner flywayRunner;

    @Autowired
    public TenantFlywayMigrator(
            DataSource dataSource,
            TenantRepository tenantRepository,
            @Value("${awgment.flyway.migrate-on-startup:true}") boolean migrateOnStartup) {
        this(dataSource, tenantRepository, migrateOnStartup, (location, schema) ->
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations(location)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .baselineOnMigrate(false)
                        .load()
                        .migrate()
        );
    }

    TenantFlywayMigrator(
            DataSource dataSource,
            TenantRepository tenantRepository,
            boolean migrateOnStartup,
            FlywayRunner flywayRunner) {
        this.dataSource = dataSource;
        this.tenantRepository = tenantRepository;
        this.migrateOnStartup = migrateOnStartup;
        this.flywayRunner = flywayRunner;
    }

    public void provisionAndMigrateTenantSchema(String tenantId) {
        String schemaName = TenantIdValidator.toSchemaName(tenantId);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to create schema for tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "'", ex);
        }
        flywayRunner.migrate(TENANT_MIGRATION_LOCATION, schemaName);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrateOnStartup) {
            return;
        }
        try {
            TenantContext.clear();
            flywayRunner.migrate(PUBLIC_MIGRATION_LOCATION, TenantContext.DEFAULT_TENANT);
            List<TenantEntity> activeTenants = tenantRepository.findByStatusOrderByTenantIdAsc(TenantStatus.ACTIVE);
            for (TenantEntity tenant : activeTenants) {
                try {
                    provisionAndMigrateTenantSchema(tenant.getTenantId());
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    @FunctionalInterface
    interface FlywayRunner {
        void migrate(String location, String schemaName);
    }
}
