package com.techsophy.tsf.account.audit;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Records sanitized, secret-free security audit entries into {@code tp_security_audit_log}
 * within the currently active schema ({@code public} or {@code tenant_<id>}).
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private static final int MAX_ACTION_LENGTH = 100;
    private static final int MAX_TARGET_TYPE_LENGTH = 64;
    private static final int MAX_TARGET_ID_LENGTH = 100;
    private static final int MAX_ACTOR_SUB_LENGTH = 100;
    private static final int MAX_RECURSION_DEPTH = 16;

    private static final Set<String> SENSITIVE_KEY_STEMS = Set.of(
            "password",
            "secret",
            "token",
            "authorization",
            "cookie",
            "jwk",
            "privatekey",
            "credential",
            "apikey",
            "bearer"
    );

    private final SecurityAuditLogRepository auditLogRepository;
    private final IdGeneratorImpl idGenerator;

    @Transactional
    public SecurityAuditLogEntity record(
            String action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            String actorSub,
            Long actorId,
            Map<String, Object> metadata) {
        return persistAuditEntry(action, targetType, targetId, outcome, actorSub, actorId, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SecurityAuditLogEntity recordIsolated(
            String action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            String actorSub,
            Long actorId,
            Map<String, Object> metadata) {
        return persistAuditEntry(action, targetType, targetId, outcome, actorSub, actorId, metadata);
    }

    private SecurityAuditLogEntity persistAuditEntry(
            String action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            String actorSub,
            Long actorId,
            Map<String, Object> metadata) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Audit action must not be blank.");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("Audit targetType must not be blank.");
        }
        Objects.requireNonNull(outcome, "outcome must not be null");

        SecurityAuditLogEntity entity = new SecurityAuditLogEntity();
        entity.setId(idGenerator.nextId().longValue());
        entity.setActorId(actorId);
        entity.setActorSub(sanitizeNullable(actorSub, MAX_ACTOR_SUB_LENGTH));
        entity.setAction(sanitizeRequired(action, MAX_ACTION_LENGTH));
        entity.setTargetType(sanitizeRequired(targetType, MAX_TARGET_TYPE_LENGTH));
        entity.setTargetId(sanitizeNullable(targetId, MAX_TARGET_ID_LENGTH));
        entity.setOutcome(outcome);
        entity.setMetadata(sanitizeMetadataMap(metadata, 0));
        entity.setCreatedOn(Instant.now());

        return auditLogRepository.save(entity);
    }

    private static String sanitizeRequired(String raw, int maxLength) {
        String sanitized = ProblemDetailFactory.sanitizeForLog(raw);
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }

    private static String sanitizeNullable(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return sanitizeRequired(raw, maxLength);
    }

    private static Map<String, Object> sanitizeMetadataMap(Map<?, ?> source, int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty() || depth > MAX_RECURSION_DEPTH) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String rawKey = String.valueOf(entry.getKey());
            if (isSensitiveKey(rawKey)) {
                continue;
            }
            String safeKey = ProblemDetailFactory.sanitizePropertyName(rawKey);
            result.put(safeKey, sanitizeValue(entry.getValue(), depth + 1));
        }
        return result;
    }

    private static Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str.isBlank() ? "" : ProblemDetailFactory.sanitizeForLog(str);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (depth > MAX_RECURSION_DEPTH) {
            return "[DEPTH_LIMIT]";
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return sanitizeMetadataMap(nestedMap, depth);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                list.add(sanitizeValue(item, depth + 1));
            }
            return list;
        }
        if (value instanceof Object[] array) {
            List<Object> list = new ArrayList<>(array.length);
            for (Object item : array) {
                list.add(sanitizeValue(item, depth + 1));
            }
            return list;
        }
        return ProblemDetailFactory.sanitizeForLog(String.valueOf(value));
    }

    private static boolean isSensitiveKey(String rawKey) {
        String normalized = rawKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String stem : SENSITIVE_KEY_STEMS) {
            if (normalized.contains(stem)) {
                return true;
            }
        }
        return false;
    }
}
