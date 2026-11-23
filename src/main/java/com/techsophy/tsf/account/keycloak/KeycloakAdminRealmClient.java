package com.techsophy.tsf.account.keycloak;

import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ApiException;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.ClientContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.IdpContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.IdpMapperContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.ProtocolMapperContract;
import com.techsophy.tsf.account.keycloak.KeycloakRealmContractSnapshot.UserProfileContract;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * with bounded HTTP connection and read timeouts.
 */
@Component
public class KeycloakAdminRealmClient implements KeycloakRealmClient {

    private static final Set<String> BUILTIN_KEYCLOAK_CLIENTS = Set.of(
            "account",
            "account-console",
            "admin-cli",
            "broker",
            "realm-management",
            "security-admin-console"
    );

    private final KeycloakAdminProperties properties;
    private final Function<KeycloakAdminProperties, Keycloak> clientFactory;

    @Autowired
    public KeycloakAdminRealmClient(KeycloakAdminProperties properties) {
        this(properties, KeycloakAdminRealmClient::buildDefaultClient);
    }

    KeycloakAdminRealmClient(
            KeycloakAdminProperties properties,
            Function<KeycloakAdminProperties, Keycloak> clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public KeycloakRealmContractSnapshot fetchRealmContract(String realmName) {
        requireRealmName(realmName);
        try (Keycloak keycloak = clientFactory.apply(properties)) {
            RealmResource realmResource = keycloak.realm(realmName);
            RealmRepresentation rep = realmResource.toRepresentation();
            if (rep == null) {
                throw new ApiException(
                        ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                        "Keycloak realm '" + ProblemDetailFactory.sanitizeForLog(realmName) + "' returned empty representation."
                );
            }

            Set<String> defaultComposites = fetchDefaultRoleComposites(realmResource, rep);
            List<IdpContract> idps = fetchIdentityProviders(realmResource);
            Map<String, List<String>> authFlows = fetchAuthFlows(realmResource);
            UserProfileContract userProfile = fetchUserProfile(realmResource);
            List<ClientContract> clients = fetchClients(realmResource);

            return new KeycloakRealmContractSnapshot(
                    rep.getRealm(),
                    Boolean.TRUE.equals(rep.isEnabled()),
                    Boolean.TRUE.equals(rep.isVerifyEmail()),
                    Boolean.TRUE.equals(rep.isRegistrationAllowed()),
                    Boolean.TRUE.equals(rep.isDuplicateEmailsAllowed()),
                    Boolean.TRUE.equals(rep.isInternationalizationEnabled()),
                    rep.getDefaultLocale(),
                    rep.getAccessTokenLifespan(),
                    defaultComposites,
                    idps,
                    authFlows,
                    userProfile,
                    clients
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to fetch Keycloak realm contract for '"
                            + ProblemDetailFactory.sanitizeForLog(realmName) + "': "
                            + ProblemDetailFactory.sanitizeForLog(ex.getMessage())
            );
        }
    }

    @Override
    public void setRealmEnabled(String realmName, boolean enabled) {
        requireRealmName(realmName);
        try (Keycloak keycloak = clientFactory.apply(properties)) {
            RealmResource realmResource = keycloak.realm(realmName);
            RealmRepresentation rep = realmResource.toRepresentation();
            rep.setEnabled(enabled);
            realmResource.update(rep);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Failed to update Keycloak realm enabled state for '"
                            + ProblemDetailFactory.sanitizeForLog(realmName) + "': "
                            + ProblemDetailFactory.sanitizeForLog(ex.getMessage())
            );
        }
    }

    private Set<String> fetchDefaultRoleComposites(RealmResource realmResource, RealmRepresentation rep) {
        Set<String> compositeNames = new LinkedHashSet<>();
        RoleRepresentation defaultRole = rep.getDefaultRole();
        if (defaultRole == null || defaultRole.getName() == null || defaultRole.getName().isBlank()) {
            return compositeNames;
        }

        Set<RoleRepresentation> direct = realmResource.roles().get(defaultRole.getName()).getRoleComposites();
        if (direct == null || direct.isEmpty()) {
            return compositeNames;
        }

        Set<String> visitedIds = new HashSet<>();
        Deque<RoleRepresentation> queue = new ArrayDeque<>(direct);
        while (!queue.isEmpty()) {
            RoleRepresentation role = queue.poll();
            if (role.getName() != null) {
                compositeNames.add(role.getName());
            }
            if (role.isComposite() && role.getId() != null && visitedIds.add(role.getId())) {
                Set<RoleRepresentation> nested = realmResource.rolesById().getRoleComposites(role.getId());
                if (nested != null) {
                    queue.addAll(nested);
                }
            }
        }
        return compositeNames;
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
                for (AuthenticationExecutionInfoRepresentation exec : executions) {
                    if (!"DISABLED".equalsIgnoreCase(exec.getRequirement())
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

    private List<ClientContract> fetchClients(RealmResource realmResource) {
        List<ClientRepresentation> clientReps = realmResource.clients().findAll();
        if (clientReps == null || clientReps.isEmpty()) {
            return List.of();
        }
        List<ClientContract> clients = new ArrayList<>(clientReps.size());
        for (ClientRepresentation rep : clientReps) {
            List<ProtocolMapperRepresentation> rawMappers = rep.getProtocolMappers();
            if ((rawMappers == null || rawMappers.isEmpty())
                    && rep.getId() != null
                    && Boolean.TRUE.equals(rep.isEnabled())
                    && !Boolean.TRUE.equals(rep.isBearerOnly())
                    && !BUILTIN_KEYCLOAK_CLIENTS.contains(rep.getClientId())) {
                rawMappers = realmResource.clients().get(rep.getId()).getProtocolMappers().getMappers();
            }
            List<ProtocolMapperContract> mappedMappers = new ArrayList<>();
            if (rawMappers != null) {
                for (ProtocolMapperRepresentation pm : rawMappers) {
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
        return clients;
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
        Client httpClient = ClientBuilder.newBuilder()
                .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .register(JacksonProvider.class)
                .build();
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
}
