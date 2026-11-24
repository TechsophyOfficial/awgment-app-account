package com.techsophy.tsf.account.it;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditLogEntity;
import com.techsophy.tsf.account.audit.SecurityAuditLogRepository;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.config.IdGeneratorConfig;
import com.techsophy.tsf.account.multitenancy.MultiTenancyHibernateConfig;
import com.techsophy.tsf.account.multitenancy.SchemaCurrentTenantIdentifierResolver;
import com.techsophy.tsf.account.multitenancy.SchemaMultiTenantConnectionProvider;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantEntity;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantRepository;
import com.techsophy.tsf.account.tenant.TenantStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Layer-2 PostgreSQL 16 integration test proving strict schema isolation
 * (`SET search_path TO tenant_<id>` with zero `, public` fallback),
 * unconditional `RESET search_path` across pooled HikariCP reuse,
 * Flyway public/tenant V1 migrations, and Hibernate 6 JPA dynamic schema routing.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        SchemaMultiTenantConnectionProvider.class,
        SchemaCurrentTenantIdentifierResolver.class,
        MultiTenancyHibernateConfig.class,
        TenantLookupService.class,
        SecurityAuditService.class,
        IdGeneratorConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MultiTenantConnectionProviderIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("awgment_account")
            .withUsername("awgment")
            .withPassword("awgment_test_pw");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/public");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("awgment.multitenancy.enabled", () -> "true");
        registry.add("awgment.flyway.migrate-on-startup", () -> "false");
    }

    @Autowired
    private SchemaMultiTenantConnectionProvider connectionProvider;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantLookupService tenantLookupService;

    @Autowired
    private SecurityAuditLogRepository auditLogRepository;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private IdGeneratorImpl idGenerator;

    @BeforeAll
    static void provisionTenantSchemasWithFlyway() throws SQLException {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setMaximumPoolSize(2);
        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration/public")
                    .schemas("public")
                    .defaultSchema("public")
                    .load()
                    .migrate();

            for (String schema : List.of("tenant_alpha", "tenant_beta")) {
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
                }
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration/tenant")
                        .schemas(schema)
                        .defaultSchema(schema)
                        .load()
                        .migrate();
            }
        }
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void shouldEnforceZeroPublicFallbackAndPreventCrossTenantLeakageUnderSmallHikariPoolContention()
            throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(1);

        try (HikariDataSource smallPool = new HikariDataSource(cfg)) {
            SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(smallPool);

            // Seed isolated rows into tenant_alpha and tenant_beta
            seedTenantUser(provider, "tenant_alpha", 101L, "alpha_user", "alpha" + "@" + "alpha.invalid");
            seedTenantUser(provider, "tenant_beta", 202L, "beta_user", "beta" + "@" + "beta.invalid");

            // Prove zero public fallback inside tenant_alpha connection
            Connection alphaConn = provider.getConnection("tenant_alpha");
            try {
                try (Statement st = alphaConn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT current_schemas(false)")) {
                    assertThat(rs.next()).isTrue();
                    String[] schemas = (String[]) rs.getArray(1).getArray();
                    assertThat(schemas).containsExactly("tenant_alpha");
                }
                assertThatThrownBy(() -> {
                    try (Statement st = alphaConn.createStatement()) {
                        st.executeQuery("SELECT count(*) FROM tp_tenant");
                    }
                }).isInstanceOf(SQLException.class);
            } finally {
                provider.releaseConnection("tenant_alpha", alphaConn);
            }

            // After release, RESET search_path must restore default search_path on raw Hikari connections
            try (Connection rawConn = smallPool.getConnection();
                 Statement st = rawConn.createStatement();
                 ResultSet rs = st.executeQuery("SHOW search_path")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).doesNotContain("tenant_alpha").doesNotContain("tenant_beta");
            }

            // Concurrent pool reuse across 8 threads over pool size 2: never leak alpha <-> beta
            int threads = 8;
            int iterations = 12;
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<Void>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    final boolean useAlpha = (t % 2 == 0);
                    final String schema = useAlpha ? "tenant_alpha" : "tenant_beta";
                    final String expectedUser = useAlpha ? "alpha_user" : "beta_user";
                    Callable<Void> job = () -> {
                        for (int i = 0; i < iterations; i++) {
                            Connection conn = provider.getConnection(schema);
                            try (Statement st = conn.createStatement();
                                 ResultSet rs = st.executeQuery("SELECT user_name FROM tp_user")) {
                                List<String> names = new ArrayList<>();
                                while (rs.next()) {
                                    names.add(rs.getString(1));
                                }
                                assertThat(names).containsExactly(expectedUser);
                            } finally {
                                provider.releaseConnection(schema, conn);
                            }
                        }
                        return null;
                    };
                    futures.add(pool.submit(job));
                }
                for (Future<Void> f : futures) {
                    f.get();
                }
            }
        }
    }

    @Test
    void shouldRouteHibernate6JpaEntitiesDynamicallyAcrossPublicAndTenantSchemas() {
        // Persist TenantEntity rows into public.tp_tenant
        TenantEntity alpha = new TenantEntity();
        alpha.setId(idGenerator.nextId().longValue());
        alpha.setTenantId("alpha");
        alpha.setSchemaName(TenantIdValidator.toSchemaName("alpha"));
        alpha.setDisplayName("Alpha Tenant");
        alpha.setStatus(TenantStatus.ACTIVE);
        tenantRepository.saveAndFlush(alpha);

        assertThat(tenantLookupService.findStatus("alpha")).contains(TenantStatus.ACTIVE);

        // Record audit log in tenant_alpha
        TenantContext.setTenantId("alpha");
        SecurityAuditLogEntity alphaLog = securityAuditService.record(
                "USER_CREATE", "USER", "u-1", AuditOutcome.SUCCESS, "op-alpha", 11L,
                Map.of("tenant", "alpha", "secret", "redact-me")
        );
        assertThat(auditLogRepository.findById(alphaLog.getId())).isPresent();
        TenantContext.clear();

        // Record audit log in tenant_beta -> alpha's audit log ID must NOT exist in tenant_beta
        TenantContext.setTenantId("beta");
        assertThat(auditLogRepository.findById(alphaLog.getId())).isEmpty();
        SecurityAuditLogEntity betaLog = securityAuditService.record(
                "USER_CREATE", "USER", "u-2", AuditOutcome.DENIED, "op-beta", 22L,
                Map.of("tenant", "beta")
        );
        assertThat(auditLogRepository.findById(betaLog.getId())).isPresent();
        TenantContext.clear();

        // Confirm tenant_alpha only sees alphaLog and NOT betaLog
        TenantContext.setTenantId("alpha");
        assertThat(auditLogRepository.findById(betaLog.getId())).isEmpty();
        TenantContext.clear();

        // Public schema still reads public.tp_tenant & public.tp_security_audit_log
        SecurityAuditLogEntity publicLog = securityAuditService.record(
                "TENANT_ACTIVATE", "TENANT", "alpha", AuditOutcome.SUCCESS, "platform-admin", 1L,
                Map.of("schema", "public")
        );
        assertThat(auditLogRepository.findById(publicLog.getId())).isPresent();

        // Verify injected connectionProvider works with real Postgres too
        assertThat(connectionProvider.supportsAggressiveRelease()).isFalse();
    }

    private static void seedTenantUser(
            SchemaMultiTenantConnectionProvider provider,
            String schema,
            long id,
            String userName,
            String email) throws SQLException {
        Connection conn = provider.getConnection(schema);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tp_user (id, user_name, email_id, identity_source, provisioning_mode, status) "
                        + "VALUES (?, ?, ?, 'KEYCLOAK_LOCAL', 'DIRECT_OPERATOR', 'ACTIVE') "
                        + "ON CONFLICT (id) DO NOTHING")) {
            ps.setLong(1, id);
            ps.setString(2, userName);
            ps.setString(3, email);
            ps.executeUpdate();
        } finally {
            provider.releaseConnection(schema, conn);
        }
    }
}
