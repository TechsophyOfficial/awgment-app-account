package com.techsophy.tsf.account.audit;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.IdGeneratorConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditServiceTest {

    private final IdGeneratorImpl idGenerator = new IdGeneratorConfig().idGenerator(1);

    @Test
    void shouldGenerateSnowflakeIdSanitizeCrlfAndStripSensitiveKeysRecursively() {
        SecurityAuditLogRepository repository = mock(SecurityAuditLogRepository.class);
        when(repository.save(any(SecurityAuditLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SecurityAuditService service = new SecurityAuditService(repository, idGenerator);

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("region", "us-east\r\nFAKE");
        nested.put("clientSecret", "super-secret-value");
        nested.put("private_key", "-----BEGIN KEY-----");
        nested.put("jwk", Map.of("kty", "RSA"));

        Map<String, Object> listItem = new LinkedHashMap<>();
        listItem.put("Authorization", "Bearer leaked");
        listItem.put("cookie", "JSESSIONID=123");
        listItem.put("step", "ok");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", "acme");
        metadata.put("password", "PlaintextPassword!");
        metadata.put("access_token", "eyJhbGciOiJSUzI1NiJ9");
        metadata.put("nested", nested);
        metadata.put("items", List.of(listItem, "line1\nline2", 42, true));
        metadata.put("blankNote", "   ");

        SecurityAuditLogEntity saved = service.record(
                "TENANT_PROVISION\r\nFORGED",
                "TENANT\tTYPE",
                "acme\n123",
                AuditOutcome.SUCCESS,
                "platform-admin\r\nsub",
                1001L,
                metadata
        );

        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(repository).save(captor.capture());

        SecurityAuditLogEntity persisted = captor.getValue();
        assertThat(saved).isSameAs(persisted);
        assertThat(persisted.getId()).isNotNull().isPositive();
        assertThat(persisted.getCreatedOn()).isNotNull();
        assertThat(persisted.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(persisted.getActorId()).isEqualTo(1001L);
        assertThat(persisted.getAction()).isEqualTo("TENANT_PROVISION_FORGED");
        assertThat(persisted.getTargetType()).isEqualTo("TENANT_TYPE");
        assertThat(persisted.getTargetId()).isEqualTo("acme_123");
        assertThat(persisted.getActorSub()).isEqualTo("platform-admin_sub");

        Map<String, Object> sanitizedMeta = persisted.getMetadata();
        assertThat(sanitizedMeta)
                .containsEntry("tenantId", "acme")
                .containsEntry("blankNote", "")
                .doesNotContainKeys("password", "access_token");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedNested = (Map<String, Object>) sanitizedMeta.get("nested");
        assertThat(sanitizedNested)
                .containsEntry("region", "us-east_FAKE")
                .doesNotContainKeys("clientSecret", "private_key", "jwk");

        @SuppressWarnings("unchecked")
        List<Object> sanitizedItems = (List<Object>) sanitizedMeta.get("items");
        assertThat(sanitizedItems).hasSize(4);
        assertThat(sanitizedItems.get(1)).isEqualTo("line1_line2");
        assertThat(sanitizedItems.get(2)).isEqualTo(42);
        assertThat(sanitizedItems.get(3)).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedListItem = (Map<String, Object>) sanitizedItems.get(0);
        assertThat(sanitizedListItem)
                .containsEntry("step", "ok")
                .doesNotContainKeys("Authorization", "cookie");
    }

    @Test
    void shouldPreserveNonNullEmptyMetadataNullOptionalFieldsAndTruncateOversizedStrings() {
        SecurityAuditLogRepository repository = mock(SecurityAuditLogRepository.class);
        when(repository.save(any(SecurityAuditLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SecurityAuditService service = new SecurityAuditService(repository, idGenerator);

        String longAction = "A".repeat(150);
        String longTargetType = "T".repeat(100);
        SecurityAuditLogEntity saved = service.record(
                longAction,
                longTargetType,
                "   ",
                AuditOutcome.DENIED,
                null,
                null,
                null
        );

        assertThat(saved.getAction()).hasSize(100);
        assertThat(saved.getTargetType()).hasSize(64);
        assertThat(saved.getTargetId()).isNull();
        assertThat(saved.getActorSub()).isNull();
        assertThat(saved.getActorId()).isNull();
        assertThat(saved.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    void shouldHandleNestedArraysDeepObjectsAndRejectMissingRequiredFields() {
        SecurityAuditLogRepository repository = mock(SecurityAuditLogRepository.class);
        when(repository.save(any(SecurityAuditLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        SecurityAuditService service = new SecurityAuditService(repository, idGenerator);

        assertThatThrownBy(() -> service.record(null, "TENANT", "acme", AuditOutcome.FAILURE, "sub", 1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record("   ", "TENANT", "acme", AuditOutcome.FAILURE, "sub", 1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record("ACT", null, "acme", AuditOutcome.FAILURE, "sub", 1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record("ACT", "   ", "acme", AuditOutcome.FAILURE, "sub", 1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record("ACT", "TENANT", "acme", null, "sub", 1L, Map.of()))
                .isInstanceOf(NullPointerException.class);

        Map<String, Object> nested = Map.of("ok", "fine");
        for (int i = 0; i < 18; i++) {
            nested = Map.of("child", nested);
        }
        Map<String, Object> deep = Map.of("level1", Map.of("token", "secret", "ok", new String[]{"a\nb", "c"}, "deepNest", nested));
        Map<String, Object> withNullKey = new LinkedHashMap<>();
        withNullKey.put(null, "ignored");
        withNullKey.put("nullableVal", null);
        withNullKey.put("status", AuditOutcome.FAILURE);
        withNullKey.put("deep", deep);

        SecurityAuditLogEntity saved = service.record(
                "TENANT_SUSPEND", "TENANT", "acme", AuditOutcome.FAILURE, "actor", 2L, withNullKey);
        assertThat(saved.getMetadata()).containsEntry("status", "FAILURE");
        assertThat(saved.getMetadata()).containsEntry("nullableVal", null);
        assertThat(saved.getMetadata()).doesNotContainKey(null);
        assertThat(saved.getMetadata().toString()).contains("DEPTH_LIMIT");

        SecurityAuditLogEntity emptyMeta = service.record(
                "TENANT_LIST", "TENANT", "all", AuditOutcome.SUCCESS, "actor", 2L, Map.of());
        assertThat(emptyMeta.getMetadata()).isEmpty();
    }
}
