package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates tenant identifiers against PostgreSQL schema safety rules and the reserved denylist.
 */
@Component
public class TenantIdValidator {

    public static final String SCHEMA_PREFIX = "tenant_";
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,49}$");
    private static final String RESERVED_PG_PREFIX = "pg_";
    private static final Set<String> RESERVED_TENANT_IDS = Set.of(
            "public",
            "master",
            "platform",
            "information_schema",
            "admin",
            "system"
    );

    public static boolean isValid(String tenantId) {
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            return false;
        }
        return !RESERVED_TENANT_IDS.contains(tenantId) && !tenantId.startsWith(RESERVED_PG_PREFIX);
    }

    public static void validate(String tenantId) {
        if (!isValid(tenantId)) {
            String safeId = ProblemDetailFactory.sanitizeForLog(tenantId);
            throw new ApiException(
                    ApiErrorCode.INVALID_TENANT_ID,
                    "Tenant identifier '" + safeId + "' is reserved or violates ^[a-z][a-z0-9_]{1,49}$."
            );
        }
    }

    public static String toSchemaName(String tenantId) {
        validate(tenantId);
        return SCHEMA_PREFIX + tenantId;
    }

    public boolean isValidTenantId(String tenantId) {
        return isValid(tenantId);
    }

    public void validateTenantId(String tenantId) {
        validate(tenantId);
    }

    public String resolveSchemaName(String tenantId) {
        return toSchemaName(tenantId);
    }
}
