package com.techsophy.tsf.account.outbox;

import com.techsophy.tsf.account.outbox.dto.OutboxEventResponse;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped REST API for inspecting and retrying coalesced Keycloak outbox events (UC-SYNC-01..02).
 */
@Validated
@RestController
@RequestMapping(path = "/accounts/v2/sync/outbox", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Sync Outbox", description = "Coalesced Keycloak outbox inspection and retry (UC-SYNC-01..02)")
public class SyncOutboxController {

    private final SyncOutboxService syncOutboxService;

    public SyncOutboxController(SyncOutboxService syncOutboxService) {
        this.syncOutboxService = syncOutboxService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('awgment-account-read', 'awgment-account-all', 'awgment-account-admin')")
    @Operation(summary = "List tenant Keycloak outbox events with optional status and aggregateType filters (UC-SYNC-01)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated outbox events"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or filter parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient authority",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PageResponse<OutboxEventResponse>> listOutbox(
            @RequestParam(required = false) OutboxStatus status,
            @RequestParam(required = false) OutboxAggregateType aggregateType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(syncOutboxService.listOutboxEvents(status, aggregateType, page, size));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyAuthority('awgment-account-create-or-update', 'awgment-account-all', 'awgment-account-admin')")
    @Operation(summary = "Reset a FAILED outbox event to PENDING and trigger immediate coalesced dispatch (UC-SYNC-02)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outbox event reset and dispatched"),
            @ApiResponse(responseCode = "403", description = "Insufficient authority",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Outbox event not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Event is not FAILED or action-email delivery is ambiguous",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OutboxEventResponse> retryOutboxEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(syncOutboxService.retryOutboxEvent(id, resolveActorSub(jwt)));
    }

    private static String resolveActorSub(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "system";
    }
}
