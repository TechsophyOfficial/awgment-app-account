package com.techsophy.tsf.account.multitenancy;

import com.techsophy.tsf.account.tenant.TenantIdValidator;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Hibernate 6 schema-per-tenant connection provider enforcing strict PostgreSQL
 * {@code SET search_path TO tenant_<id>} isolation (zero {@code public} fallback)
 * and unconditional {@code RESET search_path} with pool eviction on failure.
 */
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Pattern VALID_SCHEMA_PATTERN = Pattern.compile("^tenant_[a-z][a-z0-9_]{1,49}$");

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetSearchPathAndRelease(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        String validatedSchema = validateSchemaIdentifier(tenantIdentifier);
        Connection connection = dataSource.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + validatedSchema);
            return connection;
        } catch (SQLException | RuntimeException ex) {
            evictOrCloseOnFailure(connection, ex);
            throw ex;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        resetSearchPathAndRelease(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType)
                || DataSource.class.isAssignableFrom(unwrapType);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType)) {
            return unwrapType.cast(this);
        }
        if (DataSource.class.isAssignableFrom(unwrapType)) {
            return unwrapType.cast(dataSource);
        }
        throw new UnknownUnwrapTypeException(unwrapType);
    }

    private void resetSearchPathAndRelease(Connection connection) throws SQLException {
        if (connection == null) {
            return;
        }
        boolean resetSucceeded = false;
        try {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("RESET search_path");
            }
            resetSucceeded = true;
        } catch (SQLException | RuntimeException ex) {
            evictOrCloseOnFailure(connection, ex);
            throw ex;
        } finally {
            if (resetSucceeded) {
                connection.close();
            }
        }
    }

    private void evictOrCloseOnFailure(Connection connection, Throwable rootCause) {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.evictConnection(connection);
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeEx) {
            rootCause.addSuppressed(closeEx);
        }
    }

    private static String validateSchemaIdentifier(String tenantIdentifier) {
        if (tenantIdentifier == null) {
            throw new IllegalArgumentException("Tenant schema identifier must not be null.");
        }
        if (TenantContext.DEFAULT_TENANT.equals(tenantIdentifier)) {
            return TenantContext.DEFAULT_TENANT;
        }
        if (!VALID_SCHEMA_PATTERN.matcher(tenantIdentifier).matches()) {
            throw new IllegalArgumentException("Invalid tenant schema identifier.");
        }
        String extractedTenantId = tenantIdentifier.substring(TenantIdValidator.SCHEMA_PREFIX.length());
        if (!TenantIdValidator.isValid(extractedTenantId)) {
            throw new IllegalArgumentException("Reserved or invalid tenant identifier in schema name.");
        }
        return tenantIdentifier;
    }
}
