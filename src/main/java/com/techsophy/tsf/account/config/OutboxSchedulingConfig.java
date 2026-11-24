package com.techsophy.tsf.account.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling for the coalesced Keycloak outbox worker unless explicitly disabled.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "awgment.outbox.worker-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfig {
}
