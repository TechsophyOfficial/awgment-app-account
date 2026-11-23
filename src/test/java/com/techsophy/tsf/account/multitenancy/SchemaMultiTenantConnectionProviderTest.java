package com.techsophy.tsf.account.multitenancy;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchemaMultiTenantConnectionProviderTest {

    @Test
    void shouldSetSearchPathWithoutPublicFallbackForValidTenantSchemaAndPublicSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn1 = mock(Connection.class);
        Statement stmt1 = mock(Statement.class);
        Connection conn2 = mock(Connection.class);
        Statement stmt2 = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(conn1, conn2);
        when(conn1.createStatement()).thenReturn(stmt1);
        when(conn2.createStatement()).thenReturn(stmt2);

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource);

        Connection tenantConn = provider.getConnection("tenant_acme");
        assertThat(tenantConn).isSameAs(conn1);
        verify(stmt1).execute("SET search_path TO tenant_acme");
        verify(stmt1, never()).execute(org.mockito.ArgumentMatchers.contains(", public"));
        verify(stmt1).close();

        Connection publicConn = provider.getConnection("public");
        assertThat(publicConn).isSameAs(conn2);
        verify(stmt2).execute("SET search_path TO public");
        verify(stmt2).close();
    }

    @Test
    void shouldRejectInvalidSchemaIdentifiersBeforeTouchingDataSource() {
        DataSource dataSource = mock(DataSource.class);
        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource);

        String[] invalidSchemas = {
                null,
                "",
                "   ",
                "acme",
                "tenant_",
                "tenant_a",
                "tenant_public",
                "tenant_master",
                "tenant_platform",
                "tenant_information_schema",
                "tenant_admin",
                "tenant_system",
                "tenant_pg_catalog",
                "tenant_Acme",
                "tenant_acme; DROP TABLE tp_user",
                "tenant_acme, public",
                "tenant_" + "a" + "b".repeat(50)
        };

        for (String badSchema : invalidSchemas) {
            assertThatThrownBy(() -> provider.getConnection(badSchema))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldEvictHikariConnectionIfSetSearchPathThrowsSqlException() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);

        when(hikari.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute(anyString())).thenThrow(new SQLException("Simulated SET search_path failure"));

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(hikari);

        assertThatThrownBy(() -> provider.getConnection("tenant_acme"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Simulated SET search_path failure");

        verify(hikari).evictConnection(conn);
        verify(conn, never()).close();
    }

    @Test
    void shouldCloseGenericConnectionIfSetSearchPathThrowsSqlException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute(anyString())).thenThrow(new SQLException("SET failure"));
        doThrow(new SQLException("close also failed")).when(conn).close();

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.getConnection("tenant_acme"))
                .isInstanceOf(SQLException.class)
                .hasSuppressedException(new SQLException("close also failed"))
                .hasMessageContaining("SET failure");
        verify(conn).close();
    }

    @Test
    void shouldUnconditionallyExecuteResetSearchPathBeforeClosingConnectionOnRelease() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource);
        provider.releaseConnection("tenant_acme", conn);

        InOrder order = inOrder(stmt, conn);
        order.verify(stmt).execute("RESET search_path");
        order.verify(stmt).close();
        order.verify(conn).close();

        // Null connection release is safe no-op
        provider.releaseConnection("tenant_acme", null);
        provider.releaseAnyConnection(null);
    }

    @Test
    void shouldEvictHikariConnectionWhenResetSearchPathFailsOnRelease() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute("RESET search_path")).thenThrow(new SQLException("Connection tainted"));

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(hikari);

        assertThatThrownBy(() -> provider.releaseConnection("tenant_acme", conn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Connection tainted");

        verify(hikari).evictConnection(conn);
        verify(conn, never()).close();
    }

    @Test
    void shouldForciblyCloseNonHikariConnectionWhenResetSearchPathFailsOnRelease() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute("RESET search_path")).thenThrow(new SQLException("Reset failed"));

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.releaseConnection("tenant_acme", conn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Reset failed");

        verify(conn).close();
    }

    @Test
    void shouldSupportAnyConnectionReleaseResetUnwrapAndDisableAggressiveRelease() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource);

        assertThat(provider.supportsAggressiveRelease()).isFalse();
        assertThat(provider.getAnyConnection()).isSameAs(conn);

        provider.releaseAnyConnection(conn);
        verify(stmt).execute("RESET search_path");
        verify(conn).close();

        assertThat(provider.isUnwrappableAs(MultiTenantConnectionProvider.class)).isTrue();
        assertThat(provider.isUnwrappableAs(SchemaMultiTenantConnectionProvider.class)).isTrue();
        assertThat(provider.isUnwrappableAs(DataSource.class)).isTrue();
        assertThat(provider.isUnwrappableAs(String.class)).isFalse();

        assertThat(provider.unwrap(MultiTenantConnectionProvider.class)).isSameAs(provider);
        assertThat(provider.unwrap(DataSource.class)).isSameAs(dataSource);
        assertThatThrownBy(() -> provider.unwrap(String.class))
                .isInstanceOf(UnknownUnwrapTypeException.class);
    }
}
