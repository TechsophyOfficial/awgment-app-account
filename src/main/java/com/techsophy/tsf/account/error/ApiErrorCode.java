package com.techsophy.tsf.account.error;

import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Standardized error code catalog for /accounts/v2 (USE_CASES.md Section 7).
 */
public enum ApiErrorCode {

    // 400 Bad Request
    INVALID_TENANT_ID(HttpStatus.BAD_REQUEST, "invalid-tenant-id", "Invalid Tenant Identifier"),
    PASSWORD_MANAGED_BY_EXTERNAL_IDP(HttpStatus.BAD_REQUEST, "password-managed-by-external-idp", "Password Managed by External IdP"),
    IMMUTABLE_FIELD_MODIFICATION(HttpStatus.BAD_REQUEST, "immutable-field-modification", "Immutable Field Modification"),
    UNTRUSTED_ACL_CONTEXT(HttpStatus.BAD_REQUEST, "untrusted-acl-context", "Untrusted ACL Context"),
    INVALID_CSV_UPLOAD(HttpStatus.BAD_REQUEST, "invalid-csv-upload", "Invalid CSV Upload"),
    UNVERIFIED_IDENTITY_ATTESTATION_REQUIRED(HttpStatus.BAD_REQUEST, "unverified-identity-attestation-required", "Unverified Identity Attestation Required"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed", "Request Validation Failed"),

    // 401 Unauthorized
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "invalid-token", "Authentication Required"),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "access-denied", "Access Denied"),
    TENANT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "tenant-not-active", "Tenant Not Active"),
    TENANT_SUSPENDED(HttpStatus.FORBIDDEN, "tenant-suspended", "Tenant Suspended"),
    ACCESS_PENDING_APPROVAL(HttpStatus.FORBIDDEN, "access-pending-approval", "Access Pending Approval"),
    ACCESS_REJECTED(HttpStatus.FORBIDDEN, "access-rejected", "Access Rejected"),
    ACCESS_DISABLED(HttpStatus.FORBIDDEN, "access-disabled", "Access Disabled"),
    IDP_TRUST_REVOKED(HttpStatus.FORBIDDEN, "idp-trust-revoked", "Identity Provider Trust Revoked"),
    IDENTITY_SOURCE_MISMATCH(HttpStatus.FORBIDDEN, "identity-source-mismatch", "Identity Source Mismatch"),
    ROLE_ESCALATION_FORBIDDEN(HttpStatus.FORBIDDEN, "role-escalation-forbidden", "Role Escalation Forbidden"),
    HIGHER_TIER_MEMBER_AFFECTED(HttpStatus.FORBIDDEN, "higher-tier-member-affected", "Higher-Tier Member Affected"),
    SELF_ACTION_FORBIDDEN(HttpStatus.FORBIDDEN, "self-action-forbidden", "Self Action Forbidden"),
    RESERVED_ROLE_NAME(HttpStatus.FORBIDDEN, "reserved-role-name", "Reserved Role Name"),
    FORBIDDEN_CLIENT(HttpStatus.FORBIDDEN, "forbidden-client", "Forbidden Client"),

    // 404 Not Found
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "resource-not-found", "Resource Not Found"),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method Not Allowed"),

    // 409 Conflict
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "duplicate-resource", "Duplicate Resource"),
    EMAIL_ALREADY_CLAIMED(HttpStatus.CONFLICT, "email-already-claimed", "Email Already Claimed"),
    GROUP_HAS_SUBGROUPS(HttpStatus.CONFLICT, "group-has-subgroups", "Group Has Active Subgroups"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "invalid-state-transition", "Invalid State Transition"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent Modification"),

    // 413 Payload Too Large
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Payload Too Large"),

    // 415 Unsupported Media Type
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported Media Type"),

    // 429 Too Many Requests
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", "Rate Limit Exceeded"),

    // 500 Internal Server Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error");

    private static final String ERROR_TYPE_BASE = "https://awgment.dev/errors/";

    private final HttpStatus status;
    private final URI type;
    private final String title;

    ApiErrorCode(HttpStatus status, String slug, String title) {
        this.status = status;
        this.type = URI.create(ERROR_TYPE_BASE + slug);
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }
}
