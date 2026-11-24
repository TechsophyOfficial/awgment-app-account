package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.audit.AuditOutcome;
import com.techsophy.tsf.account.audit.SecurityAuditService;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.outbox.dto.OutboxEventResponse;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Application service backing {@link SyncOutboxController} (UC-SYNC-01..02).
 */
@Service
public class SyncOutboxService {

    private static final int MAX_PAGE_SIZE = 100;

    private final KeycloakOutboxRepository outboxRepository;
    private final OutboxStore outboxStore;
    private final OutboxService outboxService;
    private final SecurityAuditService securityAuditService;

    public SyncOutboxService(
            KeycloakOutboxRepository outboxRepository,
            OutboxStore outboxStore,
            OutboxService outboxService,
            SecurityAuditService securityAuditService) {
        this.outboxRepository = outboxRepository;
        this.outboxStore = outboxStore;
        this.outboxService = outboxService;
        this.securityAuditService = securityAuditService;
    }

    public PageResponse<OutboxEventResponse> listOutboxEvents(
            OutboxStatus status,
            OutboxAggregateType aggregateType,
            int page,
            int size) {
        if (page < 0) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Page index must be >= 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_FAILED,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + "."
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("updatedOn"), Sort.Order.desc("id"))
        );

        Page<KeycloakOutboxEventEntity> resultPage;
        if (status != null && aggregateType != null) {
            resultPage = outboxRepository.findByStatusAndAggregateType(status, aggregateType, pageable);
        } else if (status != null) {
            resultPage = outboxRepository.findByStatus(status, pageable);
        } else if (aggregateType != null) {
            resultPage = outboxRepository.findByAggregateType(aggregateType, pageable);
        } else {
            resultPage = outboxRepository.findAll(pageable);
        }
        return PageResponse.from(resultPage.map(OutboxEventResponse::from));
    }

    public OutboxEventResponse retryOutboxEvent(Long id, String actorSub) {
        KeycloakOutboxEventEntity reset = outboxStore.resetFailedForRetry(id);

        securityAuditService.record(
                "OUTBOX_RETRY",
                "OUTBOX_EVENT",
                String.valueOf(id),
                AuditOutcome.SUCCESS,
                actorSub,
                null,
                Map.of(
                        "jobType", reset.getJobType().name(),
                        "aggregateType", reset.getAggregateType().name(),
                        "aggregateId", String.valueOf(reset.getAggregateId())
                )
        );

        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !TenantContext.DEFAULT_TENANT.equals(tenantId)) {
            outboxService.dispatchPendingBatch(tenantId, 1);
        }

        return outboxRepository.findById(id)
                .map(OutboxEventResponse::from)
                .orElseGet(() -> OutboxEventResponse.from(reset));
    }
}
