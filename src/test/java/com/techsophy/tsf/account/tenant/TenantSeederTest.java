package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantSeederTest {

    @Test
    void shouldRejectInvalidTenantIdBeforeTouchingDataSource() {
        DataSource dataSource = mock(DataSource.class);
        IdGeneratorImpl idGenerator = mock(IdGeneratorImpl.class);
        TenantSeeder seeder = new TenantSeeder(dataSource, idGenerator);

        assertThatThrownBy(() -> seeder.seedTenant("public", null, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                        .isEqualTo(ApiErrorCode.INVALID_TENANT_ID));
        verifyNoInteractions(dataSource, idGenerator);
    }

    @Test
    void shouldRejectReservedSystemEmailAndMalformedBootstrapAdminEmail() {
        DataSource dataSource = mock(DataSource.class);
        IdGeneratorImpl idGenerator = mock(IdGeneratorImpl.class);
        TenantSeeder seeder = new TenantSeeder(dataSource, idGenerator);

        for (String badEmail : List.of(
                "system" + "@" + "acme.local",
                "SYSTEM" + "@" + "ACME.LOCAL",
                "not-an-email",
                "@" + "acme.invalid",
                "admin" + "@",
                "bad email" + "@" + "acme.invalid",
                "a".repeat(250) + "@" + "acme.invalid"
        )) {
            assertThatThrownBy(() -> seeder.seedTenant("acme", badEmail, "A", "B"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).errorCode())
                            .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
        verifyNoInteractions(dataSource, idGenerator);
    }

    @Test
    void shouldSeedSystemUserIdempotentlyWhenBootstrapAdminEmailIsBlank() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(ps);

        IdGeneratorImpl idGenerator = mock(IdGeneratorImpl.class);
        TenantSeeder seeder = new TenantSeeder(dataSource, idGenerator);

        seeder.seedTenant("acme", "  ", "Ignored", "User");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1)).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("\"tenant_acme\".tp_user")
                .contains("ON CONFLICT DO NOTHING");

        verify(ps).setLong(1, 1L);
        verify(ps).setString(2, "system");
        verify(ps).setString(3, "system" + "@" + "acme.local");
        verify(ps).executeUpdate();
        verifyNoInteractions(idGenerator);
    }

    @Test
    void shouldSeedSystemAndBootstrapAdminWhenEmailIsProvided() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement systemPs = mock(PreparedStatement.class);
        PreparedStatement adminPs = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(systemPs, adminPs);

        IdGeneratorImpl idGenerator = mock(IdGeneratorImpl.class);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(9001L));

        TenantSeeder seeder = new TenantSeeder(dataSource, idGenerator);
        String adminEmail = "Approver.Admin" + "@" + "acme.invalid";
        seeder.seedTenant("acme", "  " + adminEmail + " ", "Acme", "Approver");

        verify(connection, times(2)).prepareStatement(anyString());
        verify(systemPs).setLong(1, 1L);
        verify(systemPs).executeUpdate();

        verify(adminPs).setLong(1, 9001L);
        verify(adminPs).setString(2, "approver.admin" + "@" + "acme.invalid");
        verify(adminPs).setString(3, "approver.admin" + "@" + "acme.invalid");
        verify(adminPs).setString(4, "Acme");
        verify(adminPs).setString(5, "Approver");
        verify(adminPs).setString(6, "[\"awgment-account-admin\"]");
        verify(adminPs).executeUpdate();
    }

    @Test
    void shouldHandleNullAdminNamesAndTruncateLongUsername() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement systemPs = mock(PreparedStatement.class);
        PreparedStatement adminPs = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(systemPs, adminPs);

        IdGeneratorImpl idGenerator = mock(IdGeneratorImpl.class);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(9002L));

        TenantSeeder seeder = new TenantSeeder(dataSource, idGenerator);
        String longLocalPart = "a".repeat(95);
        String longEmail = longLocalPart + "@" + "very-long-acme-domain.invalid";
        seeder.seedTenant("acme", longEmail, null, "  ");

        verify(adminPs).setString(2, longEmail.substring(0, 100));
        verify(adminPs).setString(3, longEmail);
        verify(adminPs).setString(4, null);
        verify(adminPs).setString(5, null);
        verify(adminPs).executeUpdate();
    }

    @Test
    void shouldWrapSqlExceptionsCleanly() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failure"));

        TenantSeeder seeder = new TenantSeeder(dataSource, mock(IdGeneratorImpl.class));
        assertThatThrownBy(() -> seeder.seedTenant("acme", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acme");
    }
}
