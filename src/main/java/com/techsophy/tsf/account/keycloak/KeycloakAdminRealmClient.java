package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.ClientContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.IdpContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.IdpMapperContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.ProtocolMapperContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.UserProfileContract;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientScopeResource;
import org.keycloak.admin.client.resource.ClientScopesResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPConfig;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Keycloak 26 Admin Client adapter fetching realm security contracts and toggling realm enabled status
 * using a managed pooled RestEasy client with bounded HTTP connection and read timeouts.
 */
@Slf4j
@Component
public class KeycloakAdminRealmClient implements KeycloakRealmClient, AutoCloseable {

    private static final int ADMIN_POOL_SIZE = 20;
    private static final Set<String> BUILTIN_KEYCLOAK_CLIENTS = Set.of(
            "account",
            "account-console",
            "admin-cli",
            "broker",
            "realm-management",
            "security-admin-console"
    );

    private final KeycloakAdminProperties properties;
    private final AwgmentSecurityProperties securityProperties;
    private final String trustedClientId;
    private final Function<KeycloakAdminProperties, Keycloak> clientFactory;
    private final boolean reuseSharedClient;
    private final Object clientLock = new Object();
    private volatile Keycloak sharedClient;

    @Autowired
    public KeycloakAdminRealmClient(
            KeycloakAdminProperties properties,
            AwgmentSecurityProperties securityProperties) {
        this(properties, securityProperties, KeycloakAdminRealmClient::buildDefaultClient, true);
    }

    KeycloakAdminRealmClient(
            KeycloakAdminProperties properties,
            Function<KeycloakAdminProperties, Keycloak> clientFactory) {
        this.properties = properties;
        this.securityProperties = null;
        this.trustedClientId = "awgment-account";
        this.clientFactory = clientFactory;
        this.reuseSharedClient = false;
    }

    KeycloakAdminRealmClient(
            KeycloakAdminProperties properties,
            AwgmentSecurityProperties securityProperties,
            Function<KeycloakAdminProperties, Keycloak> clientFactory,
            boolean reuseSharedClient) {
        this.properties = properties;
        this.securityProperties = securityProperties;
        this.trustedClientId = securityProperties != null && securityProperties.getTrustedClientId() != null
                ? securityProperties.getTrustedClientId()
                : "awgment-account";
        this.clientFactory = clientFactory;
        this.reuseSharedClient = reuseSharedClient;
    }

    @Override
    public KeycloakRealmContractSnapshot fetchRealmContract(String realmName) {
        requireRealmName(realmName);
        String safeRealm = ProblemDetailFactory.sanitizeForLog(realmName);
        try {
            return executeWithClient(keycloak -> {
                RealmResource realmResource = keycloak.realm(realmName);
                RealmRepresentation rep = realmResource.toRepresentation();
                if (rep == null) {
                    throw new ApiException(
                            ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                            "Keycloak realm '" + safeRealm + "' returned empty representation."
                    );
                }

                DefaultRoleInfo defaultRoleInfo = fetchDefaultRoleInfo(realmResource, rep, realmName);
                Set<String> defaultGroupRoles = fetchDefaultGroupRoles(realmResource);
                Set<String> realmRoles = fetchRealmRoleNames(realmResource);
                List<IdpContract> idps = fetchIdentityProviders(realmResource);
                Map<String, List<String>> authFlows = fetchAuthFlows(realmResource);
                UserProfileContract userProfile = fetchUserProfile(realmResource);
                ClientsInspection clientsInspection = fetchClientsAndTrustedRoles(realmResource);

                return new KeycloakRealmContractSnapshot(
                        rep.getRealm(),
                        Boolean.TRUE.equals(rep.isEnabled()),
                        Boolean.TRUE.equals(rep.isVerifyEmail()),
                        Boolean.TRUE.equals(rep.isRegistrationAllowed()),
                        Boolean.TRUE.equals(rep.isDuplicateEmailsAllowed()),
                        Boolean.TRUE.equals(rep.isInternationalizationEnabled()),
                        rep.getDefaultLocale(),
                        rep.getAccessTokenLifespan(),
                        defaultRoleInfo.defaultRoleName(),
                        defaultRoleInfo.composites(),
                        defaultGroupRoles,
                        realmRoles,
                        clientsInspection.trustedClientRoles(),
                        idps,
                        authFlows,
                        userProfile,
                        clientsInspection.clients()
                );
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to fetch Keycloak realm contract for '{}': {}",
                    safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()), ex);
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to fetch Keycloak realm contract for '" + safeRealm + "'."
            );
        }
    }

    @Override
    public void setRealmEnabled(String realmName, boolean enabled) {
        requireRealmName(realmName);
        String safeRealm = ProblemDetailFactory.sanitizeForLog(realmName);
        try {
            executeWithClient(keycloak -> {
                RealmResource realmResource = keycloak.realm(realmName);
                RealmRepresentation rep = realmResource.toRepresentation();
                rep.setEnabled(enabled);
                realmResource.update(rep);
                return null;
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to update Keycloak realm enabled state for '{}': {}",
                    safeRealm, ProblemDetailFactory.sanitizeForLog(ex.getMessage()), ex);
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to update Keycloak realm enabled state for '" + safeRealm + "'."
            );
        }
    }

    @Override
    @PreDestroy
    public void close() {
        Keycloak existing;
        synchronized (clientLock) {
            existing = sharedClient;
            sharedClient = null;
        }
        if (existing != null) {
            existing.close();
        }
    }

    private <T> T executeWithClient(Function<Keycloak, T> operation) {
        if (reuseSharedClient) {
            return operation.apply(getOrCreateSharedClient());
        }
        try (Keycloak keycloak = clientFactory.apply(effectiveProperties())) {
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
                sharedClient = clientFactory.apply(effectiveProperties());
            }
            return sharedClient;
        }
    }

    private KeycloakAdminProperties effectiveProperties() {
        if (properties.getServerUrl() != null && !properties.getServerUrl().isBlank()) {
            return properties;
        }
        String fallbackUrl = null;
        if (securityProperties != null) {
            String internal = securityProperties.normalizedKeycloakInternalBaseUrl();
            fallbackUrl = !internal.isEmpty() ? internal : securityProperties.normalizedKeycloakBaseUrl();
        }
        if (fallbackUrl == null || fallbackUrl.isBlank()) {
            return properties;
        }
        KeycloakAdminProperties effective = new KeycloakAdminProperties();
        effective.setServerUrl(fallbackUrl);
        effective.setRealm(properties.getRealm());
        effective.setClientId(properties.getClientId());
        effective.setClientSecret(properties.getClientSecret());
        effective.setUsername(properties.getUsername());
        effective.setPassword(properties.getPassword());
        effective.setConnectTimeoutMs(properties.getConnectTimeoutMs());
        effective.setReadTimeoutMs(properties.getReadTimeoutMs());
        return effective;
    }

    private DefaultRoleInfo fetchDefaultRoleInfo(
            RealmResource realmResource, RealmRepresentation rep, String realmName) {
        RoleRepresentation repDefault = rep.getDefaultRole();
        String defaultRoleName = (repDefault != null && repDefault.getName() != null && !repDefault.getName().isBlank())
                ? repDefault.getName().trim()
                : ((realmName != null && !realmName.isBlank()) ? "default-roles-" + realmName : null);

        Set<String> compositeNames = new LinkedHashSet<>();
        if (defaultRoleName == null) {
            return new DefaultRoleInfo(null, compositeNames);
        }
        RolesResource rolesResource = realmResource.roles();
        if (rolesResource == null) {
            return new DefaultRoleInfo(defaultRoleName, compositeNames);
        }
        try {
            RoleResource defaultRoleResource = rolesResource.get(defaultRoleName);
            if (defaultRoleResource != null) {
                expandCompositeRoles(realmResource, defaultRoleResource.getRoleComposites(), compositeNames);
            }
        } catch (NotFoundException ignored) {
            // Realm has no default-roles-<realm> entry
        }
        return new DefaultRoleInfo(defaultRoleName, compositeNames);
    }

    private Set<String> fetchDefaultGroupRoles(RealmResource realmResource) {
        List<GroupRepresentation> defaultGroups;
        try {
            defaultGroups = realmResource.getDefaultGroups();
        } catch (NotFoundException ex) {
            return Set.of();
        }
        if (defaultGroups == null || defaultGroups.isEmpty()) {
            return Set.of();
        }
        Set<String> groupRoleNames = new LinkedHashSet<>();
        GroupsResource groupsResource = realmResource.groups();
        for (GroupRepresentation groupRep : defaultGroups) {
            if (groupRep == null) {
                continue;
            }
            if (groupRep.getRealmRoles() != null) {
                groupRep.getRealmRoles().stream()
                        .filter(name -> name != null && !name.isBlank())
                        .forEach(groupRoleNames::add);
            }
            if (groupRep.getClientRoles() != null) {
                groupRep.getClientRoles().values().stream()
                        .filter(list -> list != null)
                        .flatMap(Collection::stream)
                        .filter(name -> name != null && !name.isBlank())
                        .forEach(groupRoleNames::add);
            }
            if (groupsResource != null && groupRep.getId() != null) {
                GroupResource groupResource = groupsResource.group(groupRep.getId());
                RoleMappingResource roleMappings = groupResource != null ? groupResource.roles() : null;
                MappingsRepresentation allMappings = roleMappings != null ? roleMappings.getAll() : null;
                if (allMappings != null) {
                    expandCompositeRoles(realmResource, allMappings.getRealmMappings(), groupRoleNames);
                    if (allMappings.getClientMappings() != null) {
                        for (ClientMappingsRepresentation cm : allMappings.getClientMappings().values()) {
                            if (cm != null) {
                                expandCompositeRoles(realmResource, cm.getMappings(), groupRoleNames);
                            }
                        }
                    }
                }
            }
        }
        return groupRoleNames;
    }

    private Set<String> fetchRealmRoleNames(RealmResource realmResource) {
        RolesResource rolesResource = realmResource.roles();
        if (rolesResource == null) {
            return Set.of();
        }
        List<RoleRepresentation> listed = rolesResource.list();
        if (listed == null || listed.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (RoleRepresentation role : listed) {
            if (role != null && role.getName() != null && !role.getName().isBlank()) {
                names.add(role.getName());
            }
        }
        return names;
    }

    private void expandCompositeRoles(
            RealmResource realmResource,
            Collection<RoleRepresentation> seedRoles,
            Set<String> targetNames) {
        if (seedRoles == null || seedRoles.isEmpty()) {
            return;
        }
        Set<String> visitedIds = new HashSet<>();
        Deque<RoleRepresentation> queue = new ArrayDeque<>(seedRoles.size());
        for (RoleRepresentation seed : seedRoles) {
            if (seed != null) {
                queue.add(seed);
            }
        }
        while (!queue.isEmpty()) {
            RoleRepresentation role = queue.poll();
            if (role.getName() != null && !role.getName().isBlank()) {
                targetNames.add(role.getName());
            }
            if (role.isComposite() && role.getId() != null && visitedIds.add(role.getId())
                    && realmResource.rolesById() != null) {
                Set<RoleRepresentation> nested = realmResource.rolesById().getRoleComposites(role.getId());
                if (nested != null) {
                    for (RoleRepresentation child : nested) {
                        if (child != null) {
                            queue.add(child);
                        }
                    }
                }
            }
        }
    }

    private List<IdpContract> fetchIdentityProviders(RealmResource realmResource) {
        List<IdentityProviderRepresentation> idpReps = realmResource.identityProviders().findAll();
        if (idpReps == null || idpReps.isEmpty()) {
            return List.of();
        }
        List<IdpContract> result = new ArrayList<>(idpReps.size());
        for (IdentityProviderRepresentation idp : idpReps) {
            List<IdentityProviderMapperRepresentation> rawMappers =
                    realmResource.identityProviders().get(idp.getAlias()).getMappers();
            List<IdpMapperContract> mappers = new ArrayList<>();
            if (rawMappers != null) {
                for (IdentityProviderMapperRepresentation m : rawMappers) {
                    mappers.add(new IdpMapperContract(
                            m.getName(),
                            m.getIdentityProviderMapper(),
                            m.getConfig() != null ? m.getConfig() : Map.of()
                    ));
                }
            }
            String issuer = idp.getConfig() != null ? idp.getConfig().get("issuer") : null;
            result.add(new IdpContract(
                    idp.getAlias(),
                    idp.getProviderId(),
                    idp.isEnabled(),
                    idp.getFirstBrokerLoginFlowAlias(),
                    issuer,
                    mappers
            ));
        }
        return result;
    }

    private Map<String, List<String>> fetchAuthFlows(RealmResource realmResource) {
        List<AuthenticationFlowRepresentation> flows = realmResource.flows().getFlows();
        if (flows == null || flows.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> flowExecutions = new LinkedHashMap<>();
        for (AuthenticationFlowRepresentation flow : flows) {
            if (flow.getAlias() == null || flow.getAlias().isBlank()) {
                continue;
            }
            List<AuthenticationExecutionInfoRepresentation> executions =
                    realmResource.flows().getExecutions(flow.getAlias());
            List<String> activeProviders = new ArrayList<>();
            if (executions != null) {
                int disabledParentLevel = Integer.MAX_VALUE;
                for (AuthenticationExecutionInfoRepresentation exec : executions) {
                    int level = exec.getLevel();
                    if (level <= disabledParentLevel) {
                        disabledParentLevel = Integer.MAX_VALUE;
                    }
                    if (disabledParentLevel < Integer.MAX_VALUE) {
                        continue;
                    }
                    boolean isSubFlow = Boolean.TRUE.equals(exec.getAuthenticationFlow());
                    boolean isDisabled = "DISABLED".equalsIgnoreCase(exec.getRequirement());
                    if (isSubFlow && isDisabled) {
                        disabledParentLevel = level;
                        continue;
                    }
                    if (!isDisabled
                            && !isSubFlow
                            && exec.getProviderId() != null
                            && !exec.getProviderId().isBlank()) {
                        activeProviders.add(exec.getProviderId());
                    }
                }
            }
            flowExecutions.put(flow.getAlias(), activeProviders);
        }
        return flowExecutions;
    }

    private UserProfileContract fetchUserProfile(RealmResource realmResource) {
        UPConfig upConfig = realmResource.users().userProfile().getConfiguration();
        if (upConfig == null) {
            return null;
        }
        String policy = upConfig.getUnmanagedAttributePolicy() != null
                ? upConfig.getUnmanagedAttributePolicy().name()
                : null;
        Map<String, Set<String>> permissions = new LinkedHashMap<>();
        if (upConfig.getAttributes() != null) {
            for (UPAttribute attr : upConfig.getAttributes()) {
                if (attr.getName() == null) {
                    continue;
                }
                Set<String> editRoles = (attr.getPermissions() != null && attr.getPermissions().getEdit() != null)
                        ? attr.getPermissions().getEdit()
                        : Set.of();
                permissions.put(attr.getName(), editRoles);
            }
        }
        return new UserProfileContract(policy, permissions);
    }

    private ClientsInspection fetchClientsAndTrustedRoles(RealmResource realmResource) {
        ClientsResource clientsResource = realmResource.clients();
        List<ClientRepresentation> clientReps = clientsResource != null ? clientsResource.findAll() : null;
        if (clientReps == null || clientReps.isEmpty()) {
            return new ClientsInspection(List.of(), Set.of());
        }
        Map<String, List<ProtocolMapperRepresentation>> scopeMappersByName = fetchClientScopeMappers(realmResource);
        List<ClientContract> clients = new ArrayList<>(clientReps.size());
        Set<String> trustedClientRoles = new LinkedHashSet<>();

        for (ClientRepresentation rep : clientReps) {
            ClientResource clientResource = (rep.getId() != null && clientsResource != null)
                    ? clientsResource.get(rep.getId())
                    : null;

            if (trustedClientId.equals(rep.getClientId()) && clientResource != null) {
                RolesResource clientRolesRes = clientResource.roles();
                if (clientRolesRes != null) {
                    List<RoleRepresentation> roleReps = clientRolesRes.list();
                    if (roleReps != null) {
                        for (RoleRepresentation rr : roleReps) {
                            if (rr != null && rr.getName() != null && !rr.getName().isBlank()) {
                                trustedClientRoles.add(rr.getName());
                            }
                        }
                    }
                }
            }

            List<ProtocolMapperRepresentation> combinedMappers = new ArrayList<>();
            List<ProtocolMapperRepresentation> rawMappers = rep.getProtocolMappers();
            boolean inspectableActiveClient = Boolean.TRUE.equals(rep.isEnabled())
                    && !Boolean.TRUE.equals(rep.isBearerOnly())
                    && !BUILTIN_KEYCLOAK_CLIENTS.contains(rep.getClientId());
            if ((rawMappers == null || rawMappers.isEmpty()) && inspectableActiveClient && clientResource != null) {
                ProtocolMappersResource pmsRes = clientResource.getProtocolMappers();
                if (pmsRes != null) {
                    rawMappers = pmsRes.getMappers();
                }
            }
            if (rawMappers != null) {
                combinedMappers.addAll(rawMappers);
            }

            if (inspectableActiveClient) {
                List<String> defaultScopes = rep.getDefaultClientScopes();
                if ((defaultScopes == null || defaultScopes.isEmpty()) && clientResource != null) {
                    List<ClientScopeRepresentation> resolvedScopes = clientResource.getDefaultClientScopes();
                    if (resolvedScopes != null) {
                        for (ClientScopeRepresentation scope : resolvedScopes) {
                            if (scope == null) {
                                continue;
                            }
                            if (scope.getProtocolMappers() != null && !scope.getProtocolMappers().isEmpty()) {
                                combinedMappers.addAll(scope.getProtocolMappers());
                            } else if (scope.getName() != null) {
                                List<ProtocolMapperRepresentation> mapped = scopeMappersByName.get(scope.getName());
                                if (mapped != null) {
                                    combinedMappers.addAll(mapped);
                                }
                            }
                        }
                    }
                } else if (defaultScopes != null) {
                    for (String scopeName : defaultScopes) {
                        List<ProtocolMapperRepresentation> mapped = scopeMappersByName.get(scopeName);
                        if (mapped != null) {
                            combinedMappers.addAll(mapped);
                        }
                    }
                }
            }

            List<ProtocolMapperContract> mappedMappers = new ArrayList<>(combinedMappers.size());
            for (ProtocolMapperRepresentation pm : combinedMappers) {
                if (pm != null) {
                    mappedMappers.add(new ProtocolMapperContract(
                            pm.getName(),
                            pm.getProtocolMapper(),
                            pm.getConfig() != null ? pm.getConfig() : Map.of()
                    ));
                }
            }
            clients.add(new ClientContract(
                    rep.getClientId(),
                    Boolean.TRUE.equals(rep.isEnabled()),
                    Boolean.TRUE.equals(rep.isBearerOnly()),
                    Boolean.TRUE.equals(rep.isStandardFlowEnabled()),
                    Boolean.TRUE.equals(rep.isDirectAccessGrantsEnabled()),
                    Boolean.TRUE.equals(rep.isServiceAccountsEnabled()),
                    mappedMappers
            ));
        }
        return new ClientsInspection(clients, trustedClientRoles);
    }

    private static Map<String, List<ProtocolMapperRepresentation>> fetchClientScopeMappers(RealmResource realmResource) {
        ClientScopesResource scopesResource = realmResource.clientScopes();
        if (scopesResource == null) {
            return Map.of();
        }
        List<ClientScopeRepresentation> scopes = scopesResource.findAll();
        if (scopes == null || scopes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<ProtocolMapperRepresentation>> byName = new LinkedHashMap<>();
        for (ClientScopeRepresentation scope : scopes) {
            if (scope == null || scope.getName() == null || scope.getName().isBlank()) {
                continue;
            }
            List<ProtocolMapperRepresentation> mappers = scope.getProtocolMappers();
            if ((mappers == null || mappers.isEmpty()) && scope.getId() != null) {
                ClientScopeResource scopeResource = scopesResource.get(scope.getId());
                if (scopeResource != null && scopeResource.getProtocolMappers() != null) {
                    mappers = scopeResource.getProtocolMappers().getMappers();
                }
            }
            if (mappers != null && !mappers.isEmpty()) {
                byName.put(scope.getName(), mappers);
            }
        }
        return byName;
    }

    private static void requireRealmName(String realmName) {
        if (realmName == null || realmName.isBlank()) {
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Keycloak realm name must not be blank."
            );
        }
    }

    static Keycloak buildDefaultClient(KeycloakAdminProperties props) {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .register(JacksonProvider.class);
        if (clientBuilder instanceof ResteasyClientBuilder resteasyBuilder) {
            resteasyBuilder.connectionPoolSize(ADMIN_POOL_SIZE)
                    .maxPooledPerRoute(ADMIN_POOL_SIZE)
                    .connectionCheckoutTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
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

    private record DefaultRoleInfo(String defaultRoleName, Set<String> composites) {
    }

    private record ClientsInspection(List<ClientContract> clients, Set<String> trustedClientRoles) {
    }
}
