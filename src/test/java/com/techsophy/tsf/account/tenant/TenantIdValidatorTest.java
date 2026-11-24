package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdValidatorTest {

    private final TenantIdValidator validator = new TenantIdValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "ab",
            "a1",
            "a_",
            "pg",
            "acme",
            "acme_corp_01",
            "tenant_123"
    })
    void shouldAcceptValidTenantIdentifiers(String tenantId) {
        assertThat(TenantIdValidator.isValid(tenantId)).isTrue();
        assertThat(validator.isValidTenantId(tenantId)).isTrue();
        assertThatCode(() -> TenantIdValidator.validate(tenantId)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTenantId(tenantId)).doesNotThrowAnyException();
        assertThat(TenantIdValidator.toSchemaName(tenantId)).isEqualTo("tenant_" + tenantId);
        assertThat(validator.resolveSchemaName(tenantId)).isEqualTo("tenant_" + tenantId);
    }

    @Test
    void shouldAcceptExactMaximum50CharTenantIdentifierAndReject51Chars() {
        String fiftyChars = "a" + "b".repeat(49);
        String fiftyOneChars = "a" + "b".repeat(50);

        assertThat(fiftyChars).hasSize(50);
        assertThat(fiftyOneChars).hasSize(51);

        assertThat(TenantIdValidator.isValid(fiftyChars)).isTrue();
        assertThat(TenantIdValidator.toSchemaName(fiftyChars)).isEqualTo("tenant_" + fiftyChars);

        assertThat(TenantIdValidator.isValid(fiftyOneChars)).isFalse();
        assertThatThrownBy(() -> TenantIdValidator.validate(fiftyOneChars))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TENANT_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "a",
            "Acme",
            "ACME",
            "1acme",
            "_acme",
            "acme-corp",
            "acme.corp",
            "acme corp",
            "acme;DROP TABLE tp_user",
            "acme\r\n",
            "../etc"
    })
    void shouldRejectNullBlankAndMalformedTenantIdentifiers(String invalidId) {
        assertThat(TenantIdValidator.isValid(invalidId)).isFalse();
        assertThat(validator.isValidTenantId(invalidId)).isFalse();

        assertThatThrownBy(() -> TenantIdValidator.validate(invalidId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.errorCode()).isEqualTo(ApiErrorCode.INVALID_TENANT_ID);
                    assertThat(apiEx.getMessage()).doesNotContain("\r").doesNotContain("\n");
                    if (invalidId == null || invalidId.isBlank()) {
                        assertThat(apiEx.getMessage()).doesNotContain("'/'");
                        assertThat(apiEx.getMessage()).contains("must not be blank");
                    }
                });

        assertThatThrownBy(() -> TenantIdValidator.toSchemaName(invalidId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TENANT_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "public",
            "master",
            "platform",
            "information_schema",
            "admin",
            "system",
            "pg_",
            "pg_catalog",
            "pg_toast",
            "pg_temp_1"
    })
    void shouldRejectReservedAndPgPrefixedTenantIdentifiers(String reservedId) {
        assertThat(TenantIdValidator.isValid(reservedId)).isFalse();
        assertThatThrownBy(() -> TenantIdValidator.validate(reservedId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TENANT_ID);
        assertThatThrownBy(() -> validator.resolveSchemaName(reservedId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ApiErrorCode.INVALID_TENANT_ID);
    }
}
