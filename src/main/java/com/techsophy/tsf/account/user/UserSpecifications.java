package com.techsophy.tsf.account.user;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Indexed JPA specifications for searching and filtering {@link UserEntity} records
 * with LIKE wildcard escaping and UC-USER-03 {@code system} user visibility enforcement.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<UserEntity> buildSpecification(
            String q,
            UserStatus status,
            IdentitySource identitySource,
            String idpAlias,
            String department,
            String loginId,
            boolean includeSystemUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String trimmedLoginId = trimToNull(loginId);
            boolean allowSystem = includeSystemUser || "system".equalsIgnoreCase(trimmedLoginId);
            if (!allowSystem) {
                predicates.add(cb.notEqual(cb.lower(root.get("userName")), "system"));
            }

            String trimmedQ = trimToNull(q);
            if (trimmedQ != null) {
                String prefixPattern = escapeLike(trimmedQ.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("userName")), prefixPattern, '\\'),
                        cb.like(cb.lower(root.get("emailId")), prefixPattern, '\\'),
                        cb.like(cb.lower(root.get("firstName")), prefixPattern, '\\'),
                        cb.like(cb.lower(root.get("lastName")), prefixPattern, '\\')
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (identitySource != null) {
                predicates.add(cb.equal(root.get("identitySource"), identitySource));
            }

            String trimmedIdpAlias = trimToNull(idpAlias);
            if (trimmedIdpAlias != null) {
                predicates.add(cb.equal(root.get("idpAlias"), trimmedIdpAlias));
            }

            String trimmedDepartment = trimToNull(department);
            if (trimmedDepartment != null) {
                predicates.add(cb.equal(root.get("department"), trimmedDepartment));
            }

            if (trimmedLoginId != null) {
                String normalizedLogin = trimmedLoginId.toLowerCase(Locale.ROOT);
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("userName")), normalizedLogin),
                        cb.equal(cb.lower(root.get("emailId")), normalizedLogin)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
