package com.techsophy.tsf.account.user;

import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import com.techsophy.tsf.account.security.ServiceAccountPrincipalDetector;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.user.dto.CreateUserRequest;
import com.techsophy.tsf.account.user.dto.UpdateMyProfileRequest;
import com.techsophy.tsf.account.user.dto.UpdateUserRequest;
import com.techsophy.tsf.account.user.dto.UserDeactivationResponse;
import com.techsophy.tsf.account.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tenant-scoped REST API for user profile lifecycle and self-service management (UC-USER-01..07).
 */
@Validated
@RestController
@RequestMapping(path = "/accounts/v2/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "Tenant user profile lifecycle and /me self-service APIs (UC-USER-01..07)")
public class UserController {

    private final UserService userService;
    private final ServiceAccountPrincipalDetector serviceAccountDetector;
    private final String realmIssuerPrefix;

    public UserController(
            UserService userService,
            ServiceAccountPrincipalDetector serviceAccountDetector,
            AwgmentSecurityProperties securityProperties) {
        this.userService = userService;
        this.serviceAccountDetector = serviceAccountDetector;
        this.realmIssuerPrefix = securityProperties.normalizedKeycloakBaseUrl() + "/realms/";
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "View logged-in user's own profile (UC-USER-06)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active user's profile"),
            @ApiResponse(responseCode = "403", description = "Account disabled, pending approval, or rejected",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "User profile not found in tenant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getMyProfile(resolveCaller(jwt)));
    }

    @PutMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update logged-in user's non-ACL self-service profile fields (UC-USER-07)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation error or immutable/admin field modification",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Account disabled, pending approval, or rejected",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Optimistic lock version conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserResponse> updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.updateMyProfile(request, resolveCaller(jwt)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('awgment-account-create-or-update', 'awgment-account-all')")
    @Operation(summary = "Provision a new tenant user profile with coalesced Keycloak outbox sync (UC-USER-01)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User provisioned"),
            @ApiResponse(responseCode = "400", description = "Validation failure or unknown field",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "RBAC denial, revoked IdP trust, or role escalation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Email or username already claimed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UserResponse created = userService.createUser(request, resolveCaller(jwt));
        return ResponseEntity
                .created(URI.create("/accounts/v2/users/" + created.id()))
                .body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('awgment-account-create-or-update', 'awgment-account-all')")
    @Operation(summary = "Update an existing user profile with governance & version checks (UC-USER-01)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "400", description = "Validation error or immutable field modification",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "RBAC denial or higher-tier target guard",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Optimistic lock version conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.updateUser(id, request, resolveCaller(jwt)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('awgment-account-read', 'awgment-account-all', 'awgment-account-admin')")
    @Operation(summary = "Fetch user profile by Snowflake ID (UC-USER-02)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean onlyMandatoryFields) {
        return ResponseEntity.ok(userService.getUserById(id, onlyMandatoryFields));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('awgment-account-read', 'awgment-account-all', 'awgment-account-admin')")
    @Operation(summary = "Search, filter, sort, and paginate tenant users (UC-USER-03)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated user search results"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PageResponse<UserResponse>> searchUsers(
            @RequestParam(required = false) @Size(max = 255) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) IdentitySource identitySource,
            @RequestParam(required = false) @Size(max = 255) String idpAlias,
            @RequestParam(required = false) @Size(max = 255) String department,
            @RequestParam(required = false) @Size(max = 255) String loginId,
            @RequestParam(defaultValue = "false") boolean onlyMandatoryFields,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "createdAt,desc") @Size(max = 64) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.searchUsers(
                q,
                status,
                identitySource,
                idpAlias,
                department,
                loginId,
                onlyMandatoryFields,
                page,
                size,
                sort,
                resolveCaller(jwt)
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('awgment-account-delete', 'awgment-account-all')")
    @Operation(summary = "Deactivate (soft-delete) user with synchronous-first Keycloak revocation (UC-USER-04)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User disabled and revoked synchronously in Keycloak"),
            @ApiResponse(responseCode = "202", description = "User disabled in PostgreSQL; Keycloak revocation deferred to outbox",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDeactivationResponse.class))),
            @ApiResponse(responseCode = "403", description = "Self-disable or higher-tier target guard triggered",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<?> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        UserService.DeactivationOutcome outcome = userService.deactivateUser(id, resolveCaller(jwt));
        if (outcome.revokedSynchronously()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.response());
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('awgment-account-admin')")
    @Operation(summary = "Reactivate a disabled user account (UC-USER-05)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User reactivated"),
            @ApiResponse(responseCode = "403", description = "Requires awgment-account-admin authority",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "User is not in DISABLED state",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserResponse> reactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.reactivateUser(id, resolveCaller(jwt)));
    }

    private CallerPrincipal resolveCaller(Jwt jwt) {
        String subject = resolveSubject(jwt);
        String tenantRealm = resolveTenantRealm(jwt);
        Set<String> roles = resolveDirectRoles();
        boolean verifiedService = serviceAccountDetector.isVerifiedServiceAccount(jwt);
        return new CallerPrincipal(subject, tenantRealm, roles, verifiedService);
    }

    private static String resolveSubject(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject().trim();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName().trim();
        }
        return null;
    }

    private String resolveTenantRealm(Jwt jwt) {
        String contextTenant = TenantContext.getTenantId();
        if (contextTenant != null && !TenantContext.DEFAULT_TENANT.equals(contextTenant)) {
            return contextTenant;
        }
        if (jwt != null) {
            String issuer = jwt.getIssuer() != null
                    ? jwt.getIssuer().toString()
                    : jwt.getClaimAsString(JwtClaimNames.ISS);
            if (issuer != null && issuer.startsWith(realmIssuerPrefix)) {
                String realm = issuer.substring(realmIssuerPrefix.length());
                if (TenantIdValidator.isValid(realm)) {
                    return realm;
                }
            }
        }
        return TenantContext.DEFAULT_TENANT;
    }

    private static Set<String> resolveDirectRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority != null && authority.getAuthority() != null && !authority.getAuthority().isBlank()) {
                roles.add(authority.getAuthority());
            }
        }
        return Set.copyOf(roles);
    }
}
