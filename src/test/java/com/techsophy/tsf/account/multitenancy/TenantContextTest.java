package com.techsophy.tsf.account.multitenancy;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldDefaultToPublicSchemaAndSwitchCleanly() {
        assertThat(TenantContext.DEFAULT_TENANT).isEqualTo("public");
        assertThat(TenantContext.getTenantId()).isEqualTo("public");
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("public");

        TenantContext.setTenantId("acme");
        assertThat(TenantContext.getTenantId()).isEqualTo("acme");
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_acme");

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isEqualTo("public");
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("public");
    }

    @Test
    void shouldResetToDefaultPublicWhenNullBlankOrPublicPassed() {
        TenantContext.setTenantId("acme");
        assertThat(TenantContext.getTenantId()).isEqualTo("acme");

        TenantContext.setTenantId(null);
        assertThat(TenantContext.getTenantId()).isEqualTo("public");
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("public");

        TenantContext.setTenantId("acme");
        TenantContext.setTenantId("   ");
        assertThat(TenantContext.getTenantId()).isEqualTo("public");

        TenantContext.setTenantId("acme");
        TenantContext.setTenantId("public");
        assertThat(TenantContext.getTenantId()).isEqualTo("public");
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("public");
    }

    @Test
    void shouldRejectInvalidTenantIdWithoutCorruptingExistingContext() {
        TenantContext.setTenantId("acme");

        for (String bad : new String[]{"master", "pg_catalog", "Invalid", "a", "acme; DROP TABLE tp_user"}) {
            assertThatThrownBy(() -> TenantContext.setTenantId(bad))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).errorCode())
                    .isEqualTo(ApiErrorCode.INVALID_TENANT_ID);
            assertThat(TenantContext.getTenantId()).isEqualTo("acme");
            assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_acme");
        }
    }

    @Test
    void shouldIsolateTenantContextAcrossConcurrentThreads() throws Exception {
        TenantContext.setTenantId("main_tenant");
        CountDownLatch bothSet = new CountDownLatch(2);
        CountDownLatch proceed = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> alphaFuture = pool.submit(() -> {
                try {
                    TenantContext.setTenantId("alpha");
                    bothSet.countDown();
                    proceed.await();
                    return TenantContext.getCurrentSchema();
                } finally {
                    TenantContext.clear();
                }
            });
            Future<String> betaFuture = pool.submit(() -> {
                try {
                    TenantContext.setTenantId("beta");
                    bothSet.countDown();
                    proceed.await();
                    return TenantContext.getCurrentSchema();
                } finally {
                    TenantContext.clear();
                }
            });

            bothSet.await();
            proceed.countDown();

            assertThat(alphaFuture.get()).isEqualTo("tenant_alpha");
            assertThat(betaFuture.get()).isEqualTo("tenant_beta");
            assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_main_tenant");
        }
    }

    @Test
    void resolverAndHibernateConfigShouldBindTenantContextAndProvider() {
        SchemaCurrentTenantIdentifierResolver resolver = new SchemaCurrentTenantIdentifierResolver();
        assertThat(resolver.validateExistingCurrentSessions()).isFalse();
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("public");

        TenantContext.setTenantId("acme");
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("tenant_acme");

        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(Mockito.mock(DataSource.class));
        MultiTenancyHibernateConfig config = new MultiTenancyHibernateConfig();
        HibernatePropertiesCustomizer customizer = config.multiTenancyHibernateCustomizer(provider, resolver);

        Map<String, Object> props = new HashMap<>();
        customizer.customize(props);

        assertThat(props)
                .containsEntry(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, provider)
                .containsEntry(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
