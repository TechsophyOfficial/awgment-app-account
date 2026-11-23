package com.techsophy.tsf.account.tenant;

import com.techsophy.tsf.account.tenant.dto.CreateTenantRequest;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.tenant.dto.TenantResponse;
import com.techsophy.tsf.account.tenant.dto.UpdateTenantStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Platform Control Realm REST API for tenant provisioning, retry recovery, listing, and status transitions.
 */
@RestController
@RequestMapping(path = "/accounts/v2/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('awgment-platform-admin')")
@Tag(name = "Tenants", description = "Platform Control Realm tenant lifecycle management (UC-TENANT-01..04)")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Provision a new tenant schema, seed defaults, and verify Keycloak 26 realm contract (UC-TENANT-01)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant provisioned and active"),
            @ApiResponse(responseCode = "400", description = "Invalid tenantId or payload",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Tenant already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Keycloak realm missing or Section 3 contract violated",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        TenantResponse response = tenantService.createTenant(request, resolveActorSub(jwt));
        return ResponseEntity
                .created(URI.create("/accounts/v2/tenants/" + response.tenantId()))
                .body(response);
    }

    @PostMapping("/{tenantId}/retry-provisioning")
    @Operation(summary = "Retry failed tenant provisioning (UC-TENANT-02)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant provisioning recovered and active"),
            @ApiResponse(responseCode = "404", description = "Tenant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Tenant is not in PROVISIONING_FAILED state",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Keycloak realm contract verification failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TenantResponse> retryProvisioning(
            @PathVariable String tenantId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tenantService.retryProvisioning(tenantId, resolveActorSub(jwt)));
    }

    @GetMapping
    @Operation(summary = "List registered tenants with pagination and optional status filter (UC-TENANT-03)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of tenants"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PageResponse<TenantResponse>> listTenants(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(tenantService.listTenants(status, page, size, sort));
    }

    @PatchMapping(path = "/{tenantId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Suspend or reactivate a tenant platform-wide (UC-TENANT-04)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant status updated"),
            @ApiResponse(responseCode = "404", description = "Tenant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Version conflict or invalid state transition",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Upstream Keycloak failure or contract violation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TenantResponse> updateTenantStatus(
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tenantService.updateTenantStatus(tenantId, request, resolveActorSub(jwt)));
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
