package com.techsophy.tsf.account.audit;

/**
 * Outcome recorded for every security-relevant control-plane or tenant event.
 */
public enum AuditOutcome {
    SUCCESS,
    DENIED,
    FAILURE
}
