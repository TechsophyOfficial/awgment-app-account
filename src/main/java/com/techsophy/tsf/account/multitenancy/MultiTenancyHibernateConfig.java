package com.techsophy.tsf.account.multitenancy;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Hibernate 6 schema-per-tenant connection and identifier resolution when multi-tenancy is enabled.
 */
@Configuration
@ConditionalOnProperty(name = "awgment.multitenancy.enabled", havingValue = "true", matchIfMissing = true)
public class MultiTenancyHibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer multiTenancyHibernateCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            SchemaCurrentTenantIdentifierResolver tenantIdentifierResolver) {
        return hibernateProperties -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
