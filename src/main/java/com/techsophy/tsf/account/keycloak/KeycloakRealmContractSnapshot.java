package com.techsophy.tsf.account.keycloak;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of a Keycloak 26 realm configuration evaluated against USE_CASES.md Section 3 controls.
 */
public record KeycloakRealmContractSnapshot(
        String realmName,
        boolean enabled,
        boolean verifyEmail,
        boolean registrationAllowed,
        boolean duplicateEmailsAllowed,
        boolean internationalizationEnabled,
        String defaultLocale,
        Integer accessTokenLifespan,
        String defaultRoleName,
        Set<String> defaultRoleComposites,
        Set<String> defaultGroupRoles,
        Set<String> realmRoles,
        Set<String> trustedClientRoles,
        List<IdpContract> identityProviders,
        Map<String, List<String>> authFlowExecutions,
        UserProfileContract userProfile,
        List<ClientContract> clients
) {

    public KeycloakRealmContractSnapshot {
        defaultRoleComposites = defaultRoleComposites == null ? Set.of() : Set.copyOf(defaultRoleComposites);
        defaultGroupRoles = defaultGroupRoles == null ? Set.of() : Set.copyOf(defaultGroupRoles);
        realmRoles = realmRoles == null ? Set.of() : Set.copyOf(realmRoles);
        trustedClientRoles = trustedClientRoles == null ? Set.of() : Set.copyOf(trustedClientRoles);
        identityProviders = identityProviders == null ? List.of() : List.copyOf(identityProviders);
        authFlowExecutions = copyFlows(authFlowExecutions);
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    public KeycloakRealmContractSnapshot(
            String realmName,
            boolean enabled,
            boolean verifyEmail,
            boolean registrationAllowed,
            boolean duplicateEmailsAllowed,
            boolean internationalizationEnabled,
            String defaultLocale,
            Integer accessTokenLifespan,
            Set<String> defaultRoleComposites,
            List<IdpContract> identityProviders,
            Map<String, List<String>> authFlowExecutions,
            UserProfileContract userProfile,
            List<ClientContract> clients) {
        this(
                realmName,
                enabled,
                verifyEmail,
                registrationAllowed,
                duplicateEmailsAllowed,
                internationalizationEnabled,
                defaultLocale,
                accessTokenLifespan,
                null,
                defaultRoleComposites,
                Set.of(),
                Set.of(),
                Set.of(),
                identityProviders,
                authFlowExecutions,
                userProfile,
                clients
        );
    }

    private static Map<String, List<String>> copyFlows(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public record IdpContract(
            String alias,
            String providerId,
            boolean enabled,
            String firstBrokerLoginFlowAlias,
            String issuer,
            List<IdpMapperContract> mappers
    ) {
        public IdpContract {
            mappers = mappers == null ? List.of() : List.copyOf(mappers);
        }
    }

    public record IdpMapperContract(
            String name,
            String mapperType,
            Map<String, String> config
    ) {
        public IdpMapperContract {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record UserProfileContract(
            String unmanagedAttributePolicy,
            Map<String, Set<String>> attributeEditPermissions
    ) {
        public UserProfileContract {
            if (attributeEditPermissions == null || attributeEditPermissions.isEmpty()) {
                attributeEditPermissions = Map.of();
            } else {
                Map<String, Set<String>> copy = new LinkedHashMap<>();
                for (Map.Entry<String, Set<String>> entry : attributeEditPermissions.entrySet()) {
                    copy.put(entry.getKey(), entry.getValue() == null ? Set.of() : Set.copyOf(entry.getValue()));
                }
                attributeEditPermissions = Collections.unmodifiableMap(copy);
            }
        }
    }

    public record ClientContract(
            String clientId,
            boolean enabled,
            boolean bearerOnly,
            boolean standardFlowEnabled,
            boolean directAccessGrantsEnabled,
            boolean serviceAccountsEnabled,
            List<ProtocolMapperContract> protocolMappers
    ) {
        public ClientContract {
            protocolMappers = protocolMappers == null ? List.of() : List.copyOf(protocolMappers);
        }
    }

    public record ProtocolMapperContract(
            String name,
            String protocolMapper,
            Map<String, String> config
    ) {
        public ProtocolMapperContract {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }
}
