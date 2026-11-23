package com.techsophy.tsf.account.tenant;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Idempotently seeds baseline tenant users ({@code system} and optional bootstrap approver)
 * into {@code tenant_<id>.tp_user}.
 */
@Component
public class TenantSeeder {

    private static final long SYSTEM_USER_ID = 1L;
    private static final int MAX_USERNAME_LENGTH = 100;
    private static final String BOOTSTRAP_ADMIN_DESIRED_ROLES_JSON = "[\"awgment-account-admin\"]";

    private final DataSource dataSource;
    private final IdGeneratorImpl idGenerator;

    public TenantSeeder(DataSource dataSource, IdGeneratorImpl idGenerator) {
        this.dataSource = dataSource;
        this.idGenerator = idGenerator;
    }

    public void seedTenant(
            String tenantId,
            String bootstrapAdminEmail,
            String bootstrapAdminFirstName,
            String bootstrapAdminLastName) {
        String schemaName = TenantIdValidator.toSchemaName(tenantId);
        String systemSql = "INSERT INTO \"" + schemaName + "\".tp_user "
                + "(id, user_name, email_id, first_name, last_name, identity_source, provisioning_mode, "
                + "status, sync_status, created_by, updated_by) "
                + "VALUES (?, ?, ?, 'System', 'User', 'KEYCLOAK_LOCAL', 'DIRECT_OPERATOR', "
                + "'ACTIVE', 'SYNCED', 'system', 'system') ON CONFLICT DO NOTHING";

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement systemPs = connection.prepareStatement(systemSql)) {
                systemPs.setLong(1, SYSTEM_USER_ID);
                systemPs.setString(2, "system");
                systemPs.setString(3, "system" + "@" + tenantId + ".local");
                systemPs.executeUpdate();
            }

            if (bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank()) {
                String normalizedEmail = bootstrapAdminEmail.trim().toLowerCase(Locale.ROOT);
                String userName = normalizedEmail.length() > MAX_USERNAME_LENGTH
                        ? normalizedEmail.substring(0, MAX_USERNAME_LENGTH)
                        : normalizedEmail;
                String adminSql = "INSERT INTO \"" + schemaName + "\".tp_user "
                        + "(id, user_name, email_id, first_name, last_name, identity_source, provisioning_mode, "
                        + "status, desired_roles, sync_status, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, 'KEYCLOAK_LOCAL', 'DIRECT_OPERATOR', "
                        + "'ACTIVE', CAST(? AS jsonb), 'PENDING_SYNC', 'system', 'system') ON CONFLICT DO NOTHING";
                try (PreparedStatement adminPs = connection.prepareStatement(adminSql)) {
                    adminPs.setLong(1, idGenerator.nextId().longValue());
                    adminPs.setString(2, userName);
                    adminPs.setString(3, normalizedEmail);
                    adminPs.setString(4, trimToNull(bootstrapAdminFirstName));
                    adminPs.setString(5, trimToNull(bootstrapAdminLastName));
                    adminPs.setString(6, BOOTSTRAP_ADMIN_DESIRED_ROLES_JSON);
                    adminPs.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to seed tenant '" + ProblemDetailFactory.sanitizeForLog(tenantId) + "'", ex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
