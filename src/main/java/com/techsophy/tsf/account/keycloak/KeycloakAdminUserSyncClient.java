package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.governance.RoleCompositeExpander;
import com.techsophy.tsf.account.outbox.OutboxProperties;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import com.techsophy.tsf.account.user.IdentitySource;
import com.techsophy.tsf.account.user.UserStatus;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keycloak 26 Admin Client implementation for coalesced user reconciliation (with
 * {@code awgment_sync_version} pre-write fencing and zero password handling), bounded
 * synchronous revocation with Micrometer SLA counter, and cycle-safe composite role expansion.
 */
@Slf4j
@Component
public class KeycloakAdminUserSyncClient implements KeycloakUserSyncClient, RoleCompositeExpander, AutoCloseable {

    private static final int ADMIN_POOL_SIZE = 20;
    private static final int MIN_HTTP_TIMEOUT_MS = 100;
    private static final int MAX_HTTP_TIMEOUT_MS = 9500;
    private static final String ATTR_AWGMENT_STATUS = "awgment_status";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_AWGMENT_SYNC_VERSION = "awgment_sync_version";
    private static final String ATTR_LOCALE = "locale";
    private static final String REVOCATION_FAILED_METRIC = "awgment_revocation_sync_failed_total";
    private static final Set<String> PROTECTED_DEFAULT_ROLES = Set.of("offline_access", "uma_authorization");

    private final KeycloakAdminProperties properties;
    private final AwgmentSecurityProperties securityProperties;
    private final OutboxProperties outboxProperties;
    private final MeterRegistry meterRegistry;
    private final String trustedClientId;
    private final Function<KeycloakAdminProperties, Keycloak> clientFactory;
    private final boolean reuseSharedClient;
    private final Object clientLock = new Object();
    private final ExecutorService revocationExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "awgment-kc-revoke");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Keycloak sharedClient;

    @Autowired
    public KeycloakAdminUserSyncClient(
            KeycloakAdminProperties properties,
            AwgmentSecurityProperties securityProperties,
            OutboxProperties outboxProperties,
            MeterRegistry meterRegistry) {
        this(properties, securityProperties, outboxProperties, meterRegistry,
                KeycloakAdminUserSyncClient::buildBoundedClient, true);
    }

    KeycloakAdminUserSyncClient(
            KeycloakAdminProperties properties,
            AwgmentSecurityProperties securityProperties,
            OutboxProperties outboxProperties,
            MeterRegistry meterRegistry,
            Function<KeycloakAdminProperties, Keycloak> clientFactory,
            boolean reuseSharedClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.securityProperties = securityProperties;
        this.outboxProperties = Objects.requireNonNull(outboxProperties, "outboxProperties must not be null");
        this.meterRegistry = meterRegistry;
        this.trustedClientId = securityProperties != null && securityProperties.getTrustedClientId() != null
                && !securityProperties.getTrustedClientId().isBlank()
                ? securityProperties.getTrustedClientId().trim()
                : "awgment-account";
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
        this.reuseSharedClient = reuseSharedClient;
    }

    @Override
    public ReconcileResult reconcileUser(String realm, UserReconcilePayload payload) {
        requireRealmName(realm);
        Objects.requireNonNull(payload, "payload must not be null");
        String safeRealm = ProblemDetailFactory.sanitizeForLog(realm);
        try {
            return executeWithClient(keycloak -> {
                RealmResource realmResource = keycloak.realm(realm);
                UsersResource usersResource = realmResource.users();

                UserRepresentation existing = findExistingUser(usersResource, payload);
                if (existing != null) {
                    return updateExistingUser(realm, realmResource, usersResource, existing, payload, safeRealm);
                }

                UserRepresentation newUser = buildNewUserRepresentation(payload);
                String createdSub = createUserInKeycloak(usersResource, newUser, payload);
                if (createdSub == null) {
                    // 409 race fallback: user was created concurrently
                    UserRepresentation concurrent = findByExactUsername(usersResource, payload.userName());
                    if (concurrent == null) {
                        throw new ApiException(
                                ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                                "Keycloak reported conflict creating user in realm '" + safeRealm
                                        + "' but lookup returned empty."
                        );
                    }
                    return updateExistingUser(realm, realmResource, usersResource, concurrent, payload, safeRealm);
                }

                UserResource createdUserRes = usersResource.get(createdSub);
                if (payload.desiredRoles() != null) {
                    convergeRoles(realm, realmResource, createdUserRes, payload.desiredRoles());
                }
                if (requiresSessionLogout(payload.status())) {
                    createdUserRes.logout();
                }
                return new ReconcileResult(createdSub, false);
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to reconcile userId={} in Keycloak realm '{}': {}",
                    payload.userId(), safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()), ex);
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to reconcile user in Keycloak realm '" + safeRealm + "'."
            );
        }
    }

    @Override
    public boolean revokeUserSynchronously(String realm, String keycloakSub, long syncVersion, String statusName) {
        requireRealmName(realm);
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return false;
        }
        String safeRealm = ProblemDetailFactory.sanitizeForLog(realm);
        String targetSub = keycloakSub.trim();
        long timeoutMs = Math.max(500L, Math.min(outboxProperties.getSyncRevokeTimeoutMs(), 10 * 1000L));

        try {
            Boolean succeeded = CompletableFuture.supplyAsync(() -> executeWithClient(keycloak -> {
                UserResource userRes = keycloak.realm(realm).users().get(targetSub);
                UserRepresentation rep = userRes.toRepresentation();
                if (rep == null) {
                    return false;
                }
                if (parseSyncVersion(rep.getAttributes()) > syncVersion) {
                    return true;
                }
                rep.setEnabled(false);
                Map<String, List<String>> attrs = rep.getAttributes() != null
                        ? new LinkedHashMap<>(rep.getAttributes())
                        : new LinkedHashMap<>();
                setOrRemoveManagedAttribute(attrs, ATTR_AWGMENT_STATUS, statusName);
                setOrRemoveManagedAttribute(attrs, ATTR_AWGMENT_SYNC_VERSION, String.valueOf(syncVersion));
                rep.setAttributes(attrs);
                userRes.update(rep);
                userRes.logout();
                return true;
            }), revocationExecutor)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .get(timeoutMs + 250L, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(succeeded);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            recordRevocationFailureMetric(realm, safeRealm, ex);
            return false;
        } catch (Exception ex) {
            recordRevocationFailureMetric(realm, safeRealm, ex);
            return false;
        }
    }

    @Override
    public Set<String> expandCompositeClosure(String tenantRealm, Collection<String> directRoles) {
        if (directRoles == null || directRoles.isEmpty()) {
            return Set.of();
        }
        requireRealmName(tenantRealm);
        String safeRealm = ProblemDetailFactory.sanitizeForLog(tenantRealm);
        try {
            return executeWithClient(keycloak -> {
                RealmResource realmResource = keycloak.realm(tenantRealm);
                Map<String, RoleRepresentation> trustedRoles = indexTrustedClientRoles(realmResource);
                Map<String, RoleRepresentation> realmRoles = indexRealmRoles(realmResource);

                Set<String> expanded = new LinkedHashSet<>();
                Deque<RoleRepresentation> queue = new ArrayDeque<>();
                for (String rawRole : directRoles) {
                    if (rawRole == null || rawRole.isBlank()) {
                        continue;
                    }
                    String roleName = rawRole.trim();
                    expanded.add(roleName);
                    RoleRepresentation clientRole = trustedRoles.get(roleName);
                    if (clientRole != null) {
                        queue.add(clientRole);
                    }
                    RoleRepresentation realmRole = realmRoles.get(roleName);
                    if (realmRole != null) {
                        queue.add(realmRole);
                    }
                }
                expandBfsFromQueue(realmResource, queue, expanded);
                return Set.copyOf(expanded);
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to expand composite roles for realm '{}': {}",
                    safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()), ex);
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to resolve composite roles in Keycloak realm '" + safeRealm + "'."
            );
        }
    }

    @Override
    public Set<String> fetchUserEffectiveRoles(String tenantRealm, String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Set.of();
        }
        requireRealmName(tenantRealm);
        String safeRealm = ProblemDetailFactory.sanitizeForLog(tenantRealm);
        try {
            return executeWithClient(keycloak -> {
                RealmResource realmResource = keycloak.realm(tenantRealm);
                MappingsRepresentation allMappings;
                try {
                    allMappings = realmResource.users().get(keycloakSub.trim()).roles().getAll();
                } catch (NotFoundException ex) {
                    return Set.of();
                }
                if (allMappings == null) {
                    return Set.of();
                }
                Set<String> expanded = new LinkedHashSet<>();
                Deque<RoleRepresentation> queue = new ArrayDeque<>();
                enqueueRoleReps(allMappings.getRealmMappings(), queue);
                if (allMappings.getClientMappings() != null) {
                    ClientMappingsRepresentation trustedMappings = allMappings.getClientMappings().get(trustedClientId);
                    if (trustedMappings != null) {
                        enqueueRoleReps(trustedMappings.getMappings(), queue);
                    }
                }
                expandBfsFromQueue(realmResource, queue, expanded);
                return Set.copyOf(expanded);
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to fetch effective user roles in realm '{}': {}",
                    safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()), ex);
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to fetch user roles from Keycloak realm '" + safeRealm + "'."
            );
        }
    }

    @Override
    public void sendActionEmail(String realm, String keycloakSub, List<String> actions, int lifespanSeconds) {
        requireRealmName(realm);
        if (keycloakSub == null || keycloakSub.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "keycloakSub must not be blank.");
        }
        if (actions == null || actions.isEmpty()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Action list must not be empty.");
        }
        String safeRealm = ProblemDetailFactory.sanitizeForLog(realm);
        try {
            executeWithClient(keycloak -> {
                UserResource userRes = keycloak.realm(realm).users().get(keycloakSub.trim());
                userRes.executeActionsEmail(actions, lifespanSeconds);
                return null;
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to send Keycloak action email in realm '{}': {}",
                    safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()), ex);
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to trigger Keycloak action email in realm '" + safeRealm + "'."
            );
        }
    }

    @Override
    @PreDestroy
    public void close() {
        revocationExecutor.shutdownNow();
        Keycloak existing;
        synchronized (clientLock) {
            existing = sharedClient;
            sharedClient = null;
        }
        if (existing != null) {
            existing.close();
        }
    }

    private ReconcileResult updateExistingUser(
            String realm,
            RealmResource realmResource,
            UsersResource usersResource,
            UserRepresentation existing,
            UserReconcilePayload payload,
            String safeRealm) {
        long kcVersion = parseSyncVersion(existing.getAttributes());
        if (kcVersion > payload.syncVersion()) {
            log.info("Fenced stale Keycloak reconciliation in realm '{}': kcVersion={} > payloadVersion={}",
                    safeRealm, kcVersion, payload.syncVersion());
            return new ReconcileResult(existing.getId(), true);
        }

        existing.setEnabled(payload.status() == UserStatus.ACTIVE);
        if (payload.identitySource() == IdentitySource.KEYCLOAK_LOCAL) {
            existing.setFirstName(payload.firstName());
            existing.setLastName(payload.lastName());
            existing.setEmail(payload.emailId());
        }

        Map<String, List<String>> attrs = existing.getAttributes() != null
                ? new LinkedHashMap<>(existing.getAttributes())
                : new LinkedHashMap<>();
        applyManagedAttributes(attrs, payload);
        existing.setAttributes(attrs);

        UserResource userRes = usersResource.get(existing.getId());
        userRes.update(existing);

        if (payload.desiredRoles() != null) {
            convergeRoles(realm, realmResource, userRes, payload.desiredRoles());
        }
        if (requiresSessionLogout(payload.status())) {
            userRes.logout();
        }
        return new ReconcileResult(existing.getId(), false);
    }

    private static UserRepresentation buildNewUserRepresentation(UserReconcilePayload payload) {
        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(payload.userName());
        newUser.setEmail(payload.emailId());
        newUser.setEnabled(payload.status() == UserStatus.ACTIVE);
        newUser.setFirstName(payload.firstName());
        newUser.setLastName(payload.lastName());
        // Never set credentials / plaintext passwords
        Map<String, List<String>> attrs = new LinkedHashMap<>();
        applyManagedAttributes(attrs, payload);
        newUser.setAttributes(attrs);
        return newUser;
    }

    private static void applyManagedAttributes(Map<String, List<String>> attrs, UserReconcilePayload payload) {
        setOrRemoveManagedAttribute(attrs, ATTR_AWGMENT_STATUS,
                payload.status() != null ? payload.status().name() : null);
        setOrRemoveManagedAttribute(attrs, ATTR_USER_ID, String.valueOf(payload.userId()));
        setOrRemoveManagedAttribute(attrs, ATTR_AWGMENT_SYNC_VERSION, String.valueOf(payload.syncVersion()));
        setOrRemoveManagedAttribute(attrs, ATTR_LOCALE, normalizeLocale(payload.locale()));
    }

    private static void setOrRemoveManagedAttribute(Map<String, List<String>> attrs, String key, String value) {
        if (value == null || value.isBlank()) {
            attrs.remove(key);
        } else {
            attrs.put(key, List.of(value.trim()));
        }
    }

    private String createUserInKeycloak(
            UsersResource usersResource,
            UserRepresentation newUser,
            UserReconcilePayload payload) {
        try (Response response = usersResource.create(newUser)) {
            int statusCode = response.getStatus();
            if (statusCode == Response.Status.CONFLICT.getStatusCode()) {
                return null;
            }
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiException(
                        ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                        "Keycloak user create failed for userId=" + payload.userId() + " with HTTP " + statusCode
                );
            }
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private UserRepresentation findExistingUser(UsersResource usersResource, UserReconcilePayload payload) {
        if (payload.keycloakSub() != null && !payload.keycloakSub().isBlank()) {
            try {
                UserRepresentation bySub = usersResource.get(payload.keycloakSub().trim()).toRepresentation();
                if (bySub != null) {
                    return bySub;
                }
            } catch (NotFoundException ignored) {
                // Fall back to username lookup
            }
        }
        return findByExactUsername(usersResource, payload.userName());
    }

    private static UserRepresentation findByExactUsername(UsersResource usersResource, String userName) {
        if (userName == null || userName.isBlank()) {
            return null;
        }
        List<UserRepresentation> matches = usersResource.searchByUsername(userName.trim(), true);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        for (UserRepresentation candidate : matches) {
            if (candidate != null && candidate.getUsername() != null
                    && candidate.getUsername().equalsIgnoreCase(userName.trim())) {
                return candidate;
            }
        }
        return null;
    }

    private void convergeRoles(
            String realm,
            RealmResource realmResource,
            UserResource userRes,
            List<String> desiredRoles) {
        RoleMappingResource roleMappings = userRes.roles();
        if (roleMappings == null) {
            return;
        }

        Set<String> desiredSet = normalizeRoles(desiredRoles);
        String clientUuid = findTrustedClientUuid(realmResource);
        Map<String, RoleRepresentation> availableClientRoles = clientUuid != null
                ? indexTrustedClientRoles(realmResource, clientUuid)
                : Map.of();
        Map<String, RoleRepresentation> availableRealmRoles = indexRealmRoles(realmResource);

        if (clientUuid != null) {
            List<RoleRepresentation> currentClientRoles = safeList(roleMappings.clientLevel(clientUuid).listAll());
            Set<String> currentClientNames = currentClientRoles.stream()
                    .map(RoleRepresentation::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<RoleRepresentation> clientToAdd = new ArrayList<>();
            for (String roleName : desiredSet) {
                if (!currentClientNames.contains(roleName)) {
                    RoleRepresentation rep = availableClientRoles.get(roleName);
                    if (rep != null) {
                        clientToAdd.add(rep);
                    }
                }
            }
            List<RoleRepresentation> clientToRemove = new ArrayList<>();
            for (RoleRepresentation current : currentClientRoles) {
                if (current.getName() != null && !desiredSet.contains(current.getName())) {
                    clientToRemove.add(current);
                }
            }
            if (!clientToAdd.isEmpty()) {
                roleMappings.clientLevel(clientUuid).add(clientToAdd);
            }
            if (!clientToRemove.isEmpty()) {
                roleMappings.clientLevel(clientUuid).remove(clientToRemove);
            }
        }

        String defaultRealmRole = "default-roles-" + realm;
        List<RoleRepresentation> currentRealmRoles = safeList(roleMappings.realmLevel().listAll());
        Set<String> currentRealmNames = currentRealmRoles.stream()
                .map(RoleRepresentation::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<RoleRepresentation> realmToAdd = new ArrayList<>();
        for (String roleName : desiredSet) {
            if (!availableClientRoles.containsKey(roleName)
                    && !currentRealmNames.contains(roleName)
                    && !defaultRealmRole.equals(roleName)
                    && !PROTECTED_DEFAULT_ROLES.contains(roleName)) {
                RoleRepresentation rep = availableRealmRoles.get(roleName);
                if (rep != null) {
                    realmToAdd.add(rep);
                }
            }
        }
        List<RoleRepresentation> realmToRemove = new ArrayList<>();
        for (RoleRepresentation current : currentRealmRoles) {
            String name = current.getName();
            if (name != null
                    && !defaultRealmRole.equals(name)
                    && !PROTECTED_DEFAULT_ROLES.contains(name)
                    && !desiredSet.contains(name)) {
                realmToRemove.add(current);
            }
        }
        if (!realmToAdd.isEmpty()) {
            roleMappings.realmLevel().add(realmToAdd);
        }
        if (!realmToRemove.isEmpty()) {
            roleMappings.realmLevel().remove(realmToRemove);
        }
    }

    private String findTrustedClientUuid(RealmResource realmResource) {
        ClientsResource clientsResource = realmResource.clients();
        if (clientsResource == null) {
            return null;
        }
        List<ClientRepresentation> found = clientsResource.findByClientId(trustedClientId);
        if (found == null || found.isEmpty() || found.getFirst() == null) {
            return null;
        }
        return found.getFirst().getId();
    }

    private Map<String, RoleRepresentation> indexTrustedClientRoles(RealmResource realmResource) {
        String clientUuid = findTrustedClientUuid(realmResource);
        return clientUuid != null ? indexTrustedClientRoles(realmResource, clientUuid) : Map.of();
    }

    private Map<String, RoleRepresentation> indexTrustedClientRoles(RealmResource realmResource, String clientUuid) {
        ClientResource clientResource = realmResource.clients().get(clientUuid);
        if (clientResource == null || clientResource.roles() == null) {
            return Map.of();
        }
        return indexRolesByName(clientResource.roles().list());
    }

    private static Map<String, RoleRepresentation> indexRealmRoles(RealmResource realmResource) {
        RolesResource rolesResource = realmResource.roles();
        if (rolesResource == null) {
            return Map.of();
        }
        return indexRolesByName(rolesResource.list());
    }

    private static Map<String, RoleRepresentation> indexRolesByName(List<RoleRepresentation> roles) {
        if (roles == null || roles.isEmpty()) {
            return Map.of();
        }
        Map<String, RoleRepresentation> map = new LinkedHashMap<>(roles.size());
        for (RoleRepresentation role : roles) {
            if (role != null && role.getName() != null && !role.getName().isBlank()) {
                map.put(role.getName().trim(), role);
            }
        }
        return map;
    }

    private static void expandBfsFromQueue(
            RealmResource realmResource,
            Deque<RoleRepresentation> queue,
            Set<String> expanded) {
        Set<String> visitedCompositeIds = new HashSet<>();
        while (!queue.isEmpty()) {
            RoleRepresentation role = queue.poll();
            if (role.getName() != null && !role.getName().isBlank()) {
                expanded.add(role.getName().trim());
            }
            if (role.isComposite() && role.getId() != null && visitedCompositeIds.add(role.getId())
                    && realmResource.rolesById() != null) {
                try {
                    Set<RoleRepresentation> children = realmResource.rolesById().getRoleComposites(role.getId());
                    enqueueRoleReps(children, queue);
                } catch (NotFoundException ignored) {
                    // Composite role disappeared concurrently; skip cleanly
                }
            }
        }
    }

    private static void enqueueRoleReps(Collection<RoleRepresentation> reps, Deque<RoleRepresentation> queue) {
        if (reps == null || reps.isEmpty()) {
            return;
        }
        for (RoleRepresentation rep : reps) {
            if (rep != null) {
                queue.add(rep);
            }
        }
    }

    private void recordRevocationFailureMetric(String realm, String safeRealm, Exception ex) {
        if (meterRegistry != null) {
            meterRegistry.counter(REVOCATION_FAILED_METRIC, "tenant", realm).increment();
        }
        log.warn("Synchronous Keycloak user revocation failed for realm '{}': {}",
                safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()));
    }

    private <T> T executeWithClient(Function<Keycloak, T> operation) {
        if (reuseSharedClient) {
            return operation.apply(getOrCreateSharedClient());
        }
        try (Keycloak keycloak = clientFactory.apply(effectiveBoundedProperties())) {
            return operation.apply(keycloak);
        }
    }

    private Keycloak getOrCreateSharedClient() {
        Keycloak current = sharedClient;
        if (current != null && !current.isClosed()) {
            return current;
        }
        synchronized (clientLock) {
            if (sharedClient == null || sharedClient.isClosed()) {
                sharedClient = clientFactory.apply(effectiveBoundedProperties());
            }
            return sharedClient;
        }
    }

    private KeycloakAdminProperties effectiveBoundedProperties() {
        String serverUrl = properties.getServerUrl();
        if ((serverUrl == null || serverUrl.isBlank()) && securityProperties != null) {
            String internal = securityProperties.normalizedKeycloakInternalBaseUrl();
            serverUrl = !internal.isEmpty() ? internal : securityProperties.normalizedKeycloakBaseUrl();
        }
        KeycloakAdminProperties bounded = new KeycloakAdminProperties();
        bounded.setServerUrl(serverUrl != null && !serverUrl.isBlank() ? serverUrl : "http://localhost:8080");
        bounded.setRealm(properties.getRealm());
        bounded.setClientId(properties.getClientId());
        bounded.setClientSecret(properties.getClientSecret());
        bounded.setUsername(properties.getUsername());
        bounded.setPassword(properties.getPassword());
        bounded.setConnectTimeoutMs(clampTimeoutMs(properties.getConnectTimeoutMs()));
        bounded.setReadTimeoutMs(clampTimeoutMs(properties.getReadTimeoutMs()));
        return bounded;
    }

    static int clampTimeoutMs(int timeoutMs) {
        return Math.min(Math.max(timeoutMs, MIN_HTTP_TIMEOUT_MS), MAX_HTTP_TIMEOUT_MS);
    }

    static Keycloak buildBoundedClient(KeycloakAdminProperties props) {
        int connectTimeout = clampTimeoutMs(props.getConnectTimeoutMs());
        int readTimeout = clampTimeoutMs(props.getReadTimeoutMs());
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .register(JacksonProvider.class);
        if (clientBuilder instanceof ResteasyClientBuilder resteasyBuilder) {
            resteasyBuilder.connectionPoolSize(ADMIN_POOL_SIZE)
                    .maxPooledPerRoute(ADMIN_POOL_SIZE)
                    .connectionCheckoutTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                    .setFollowRedirects(false);
        }
        Client httpClient = clientBuilder.build();
        try {
            KeycloakBuilder builder = KeycloakBuilder.builder()
                    .serverUrl(props.getServerUrl())
                    .realm(props.getRealm())
                    .clientId(props.getClientId())
                    .resteasyClient(httpClient);
            if (props.getClientSecret() != null && !props.getClientSecret().isBlank()) {
                builder.grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                        .clientSecret(props.getClientSecret());
            } else {
                builder.grantType(OAuth2Constants.PASSWORD)
                        .username(props.getUsername())
                        .password(props.getPassword());
            }
            return builder.build();
        } catch (RuntimeException ex) {
            httpClient.close();
            throw ex;
        }
    }

    private static long parseSyncVersion(Map<String, List<String>> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return 0L;
        }
        List<String> values = attributes.get(ATTR_AWGMENT_SYNC_VERSION);
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        String raw = values.getFirst();
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String normalizeLocale(String locale) {
        return (locale == null || locale.isBlank()) ? null : locale.trim();
    }

    private static boolean requiresSessionLogout(UserStatus status) {
        return status == UserStatus.DISABLED || status == UserStatus.REJECTED;
    }

    private static Set<String> normalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                result.add(role.trim());
            }
        }
        return result;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void requireRealmName(String realmName) {
        if (realmName == null || realmName.isBlank()) {
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Keycloak realm name must not be blank."
            );
        }
    }
}
