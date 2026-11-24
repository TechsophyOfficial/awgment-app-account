package com.techsophy.tsf.account.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Hibernate 6 tenant identifier resolver delegating to {@link TenantContext#getCurrentSchema()}.
 */
@Component
public class SchemaCurrentTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
