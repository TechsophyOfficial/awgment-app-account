package com.techsophy.tsf.account.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Typed configuration properties for the coalesced Keycloak outbox engine ({@code awgment.outbox.*}).
 */
@Validated
@Component
@ConfigurationProperties(prefix = "awgment.outbox")
public class OutboxProperties {

    private static final int MAX_WORKER_ID_LENGTH = 100;

    private final String defaultWorkerId = computeDefaultWorkerId();

    private boolean workerEnabled = true;

    @Min(100)
    private long pollIntervalMs = 2000L;

    @Min(5)
    private int leaseTtlSeconds = 60;

    @Min(1)
    private int verificationDelaySeconds = 15;

    @Min(500)
    @Max(10 * 1000)
    private long syncRevokeTimeoutMs = 5000L;

    @Min(1)
    private int maxRetries = 5;

    @Min(1)
    @Max(200)
    private int batchSize = 20;

    @Min(1)
    private int baseBackoffSeconds = 5;

    @Min(5)
    private int maxBackoffSeconds = 300;

    private boolean actionEmailDeliveryEnabled = false;

    private String workerId = "";

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getLeaseTtlSeconds() {
        return leaseTtlSeconds;
    }

    public void setLeaseTtlSeconds(int leaseTtlSeconds) {
        this.leaseTtlSeconds = leaseTtlSeconds;
    }

    public int getVerificationDelaySeconds() {
        return verificationDelaySeconds;
    }

    public void setVerificationDelaySeconds(int verificationDelaySeconds) {
        this.verificationDelaySeconds = verificationDelaySeconds;
    }

    public long getSyncRevokeTimeoutMs() {
        return syncRevokeTimeoutMs;
    }

    public void setSyncRevokeTimeoutMs(long syncRevokeTimeoutMs) {
        this.syncRevokeTimeoutMs = syncRevokeTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBaseBackoffSeconds() {
        return baseBackoffSeconds;
    }

    public void setBaseBackoffSeconds(int baseBackoffSeconds) {
        this.baseBackoffSeconds = baseBackoffSeconds;
    }

    public int getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(int maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    public boolean isActionEmailDeliveryEnabled() {
        return actionEmailDeliveryEnabled;
    }

    public void setActionEmailDeliveryEnabled(boolean actionEmailDeliveryEnabled) {
        this.actionEmailDeliveryEnabled = actionEmailDeliveryEnabled;
    }

    public String getWorkerId() {
        if (!workerId.isBlank()) {
            String trimmed = workerId.trim();
            return trimmed.length() > MAX_WORKER_ID_LENGTH
                    ? trimmed.substring(0, MAX_WORKER_ID_LENGTH)
                    : trimmed;
        }
        return defaultWorkerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId == null ? "" : workerId;
    }

    static String formatWorkerId(String resolvedHost, String randomUuid) {
        String host = (resolvedHost == null || resolvedHost.isBlank())
                ? "worker"
                : resolvedHost.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        String suffix = "-" + randomUuid.substring(0, 8);
        int maxHostLen = MAX_WORKER_ID_LENGTH - suffix.length();
        String boundedHost = host.length() > maxHostLen ? host.substring(0, maxHostLen) : host;
        return boundedHost + suffix;
    }

    private static String computeDefaultWorkerId() {
        String resolvedHost = null;
        try {
            resolvedHost = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            // Fall back to static prefix
        }
        return formatWorkerId(resolvedHost, UUID.randomUUID().toString());
    }
}
