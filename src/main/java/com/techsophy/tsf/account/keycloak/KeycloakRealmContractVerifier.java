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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that a tenant's Keycloak 26 realm enforces all USE_CASES.md Section 3 security controls
 * prior to admitting traffic on tenant provisioning or reactivation.
 */
@Component
public class KeycloakRealmContractVerifier {

    private static final int MAX_ACCESS_TOKEN_LIFESPAN_SECONDS = 300;
    private static final Set<String> REQUIRED_ADMIN_LOCKED_ATTRIBUTES = Set.of(
            "awgment_status",
            "userId",
            "awgment_sync_version",
            "locale"
    );
    private static final Set<String> FORBIDDEN_BROKER_AUTHENTICATORS = Set.of(
            "idp-auto-link",
            "idp-confirm-link",
            "idp-detect-existing-broker-user",
            "idp-email-verification"
    );
    private static final Set<String> BUILTIN_KEYCLOAK_CLIENTS = Set.of(
            "account",
            "account-console",
            "admin-cli",
            "broker",
            "realm-management",
            "security-admin-console"
    );
    private static final List<String> FORBIDDEN_ISSUER_FRAGMENTS = List.of(
            "/common",
            "/organizations",
            "/consumers"
    );

    private final String expectedAudience;
    private final String platformRealm;

    @Autowired
    public KeycloakRealmContractVerifier(AwgmentSecurityProperties securityProperties) {
        this(securityProperties.getExpectedAudience(), securityProperties.getPlatformRealm());
    }

    KeycloakRealmContractVerifier(String expectedAudience, String platformRealm) {
        this.expectedAudience = expectedAudience;
        this.platformRealm = platformRealm;
    }

    public void verify(KeycloakRealmContractSnapshot snapshot) {
        if (snapshot == null) {
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Keycloak realm contract snapshot must not be null."
            );
        }

        List<String> violations = new ArrayList<>();
        verifyRealmSettings(snapshot, violations);
        verifyIdentityProviders(snapshot, violations);
        verifyUserProfile(snapshot, violations);
        verifyClients(snapshot, violations);

        if (!violations.isEmpty()) {
            throw new ApiException(
                    ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                    "Keycloak realm '" + ProblemDetailFactory.sanitizeForLog(snapshot.realmName())
                            + "' violates Section 3 security contract: " + String.join("; ", violations)
            );
        }
    }

    private void verifyRealmSettings(KeycloakRealmContractSnapshot snapshot, List<String> violations) {
        String realm = snapshot.realmName();
        if (realm == null || realm.isBlank()) {
            violations.add("realmName must not be blank");
        } else if (realm.equals(platformRealm) || "master".equals(realm)) {
            violations.add("tenant realm cannot be reserved platform/master realm '" + realm + "'");
        }
        if (!snapshot.enabled()) {
            violations.add("realm must be enabled");
        }
        if (snapshot.accessTokenLifespan() == null
                || snapshot.accessTokenLifespan() <= 0
                || snapshot.accessTokenLifespan() > MAX_ACCESS_TOKEN_LIFESPAN_SECONDS) {
            violations.add("accessTokenLifespan must be between 1 and " + MAX_ACCESS_TOKEN_LIFESPAN_SECONDS + " seconds");
        }
        if (snapshot.duplicateEmailsAllowed()) {
            violations.add("duplicateEmailsAllowed must be false");
        }
        if (snapshot.registrationAllowed() && !snapshot.verifyEmail()) {
            violations.add("verifyEmail must be true when registrationAllowed is true");
        }
        if (!snapshot.internationalizationEnabled()) {
            violations.add("internationalizationEnabled must be true");
        }
        if (snapshot.defaultLocale() == null || snapshot.defaultLocale().isBlank()) {
            violations.add("defaultLocale must not be blank");
        }
        for (String role : snapshot.defaultRoleComposites()) {
            if (role != null && role.toLowerCase(Locale.ROOT).startsWith("awgment-")) {
                violations.add("default role composites must not include Awgment role '" + role + "'");
            }
        }
    }

    private void verifyIdentityProviders(KeycloakRealmContractSnapshot snapshot, List<String> violations) {
        Map<String, List<String>> flows = snapshot.authFlowExecutions();
        for (IdpContract idp : snapshot.identityProviders()) {
            if (!idp.enabled() || !isOidcProvider(idp.providerId())) {
                continue;
            }
            String alias = idp.alias() != null ? idp.alias() : "unknown";
            String issuer = idp.issuer();
            if (issuer == null || issuer.isBlank()) {
                violations.add("OIDC IdP '" + alias + "' must configure a non-blank issuer");
            } else {
                String lowerIssuer = issuer.toLowerCase(Locale.ROOT);
                for (String fragment : FORBIDDEN_ISSUER_FRAGMENTS) {
                    if (lowerIssuer.contains(fragment)) {
                        violations.add("OIDC IdP '" + alias + "' issuer must be single-tenant (contains '" + fragment + "')");
                    }
                }
            }

            boolean hasTenantIdMapper = idp.mappers().stream().anyMatch(KeycloakRealmContractVerifier::isIdpTenantNoteMapper);
            if (!hasTenantIdMapper) {
                violations.add("OIDC IdP '" + alias + "' must define an oidc-user-session-note-idp-mapper for idp_tenant_id");
            }

            String flowAlias = idp.firstBrokerLoginFlowAlias();
            if (flowAlias == null || flowAlias.isBlank() || !flows.containsKey(flowAlias)) {
                violations.add("OIDC IdP '" + alias + "' must bind an existing firstBrokerLoginFlowAlias");
            } else {
                List<String> executions = flows.get(flowAlias);
                if (executions == null || executions.isEmpty()) {
                    violations.add("firstBrokerLoginFlowAlias '" + flowAlias + "' for IdP '" + alias + "' has no executions");
                } else {
                    for (String execution : executions) {
                        if (execution != null && FORBIDDEN_BROKER_AUTHENTICATORS.contains(execution.toLowerCase(Locale.ROOT))) {
                            violations.add("firstBrokerLoginFlowAlias '" + flowAlias + "' must not execute '" + execution + "'");
                        }
                    }
                }
            }
        }
    }

    private void verifyUserProfile(KeycloakRealmContractSnapshot snapshot, List<String> violations) {
        UserProfileContract userProfile = snapshot.userProfile();
        if (userProfile == null) {
            violations.add("Declarative UserProfile configuration is missing");
            return;
        }
        String policy = userProfile.unmanagedAttributePolicy();
        if (policy != null && !"ADMIN_EDIT".equals(policy)) {
            violations.add("UserProfile unmanagedAttributePolicy must be null or ADMIN_EDIT, found '" + policy + "'");
        }
        Map<String, Set<String>> permissions = userProfile.attributeEditPermissions();
        for (String requiredAttr : REQUIRED_ADMIN_LOCKED_ATTRIBUTES) {
            Set<String> editRoles = permissions.get(requiredAttr);
            if (editRoles == null || !Set.of("admin").equals(editRoles)) {
                violations.add("UserProfile attribute '" + requiredAttr + "' must exist with edit permissions [admin]");
            }
        }
    }

    private void verifyClients(KeycloakRealmContractSnapshot snapshot, List<String> violations) {
        int interactiveCount = 0;
        for (ClientContract client : snapshot.clients()) {
            String clientId = client.clientId() != null ? client.clientId() : "unknown";
            if (!client.enabled() || client.bearerOnly() || BUILTIN_KEYCLOAK_CLIENTS.contains(clientId)) {
                continue;
            }
            boolean isInteractive = client.standardFlowEnabled() || client.directAccessGrantsEnabled();
            if (isInteractive) {
                interactiveCount++;
                if (!hasAudienceMapper(client, expectedAudience)) {
                    violations.add("Interactive client '" + clientId
                            + "' must define oidc-audience-mapper for '" + expectedAudience + "'");
                }
                if (!hasSessionNoteMapper(client, "identity_provider")) {
                    violations.add("Interactive client '" + clientId
                            + "' must define oidc-usersessionmodel-note-mapper for 'identity_provider'");
                }
                if (!hasSessionNoteMapper(client, "idp_tenant_id")) {
                    violations.add("Interactive client '" + clientId
                            + "' must define oidc-usersessionmodel-note-mapper for 'idp_tenant_id'");
                }
            } else if (client.serviceAccountsEnabled() && !"awgment-admin-sync".equals(clientId)) {
                if (!hasAudienceMapper(client, expectedAudience)) {
                    violations.add("Service-account client '" + clientId
                            + "' must define oidc-audience-mapper for '" + expectedAudience + "'");
                }
            }
        }
        if (interactiveCount == 0) {
            violations.add("Realm must configure at least one enabled interactive login client");
        }
    }

    private static boolean isOidcProvider(String providerId) {
        return providerId != null
                && ("oidc".equalsIgnoreCase(providerId) || "keycloak-oidc".equalsIgnoreCase(providerId));
    }

    private static boolean isIdpTenantNoteMapper(IdpMapperContract mapper) {
        return "oidc-user-session-note-idp-mapper".equalsIgnoreCase(mapper.mapperType())
                && "idp_tenant_id".equals(mapper.config().get("session.note"));
    }

    private static boolean hasAudienceMapper(ClientContract client, String expectedAudience) {
        for (ProtocolMapperContract pm : client.protocolMappers()) {
            if (!"oidc-audience-mapper".equals(pm.protocolMapper())) {
                continue;
            }
            Map<String, String> cfg = pm.config();
            String clientAud = cfg.get("included.client.audience");
            String customAud = cfg.get("included.custom.audience");
            boolean matchesAudience = expectedAudience.equals(clientAud) || expectedAudience.equals(customAud);
            if (matchesAudience && "true".equalsIgnoreCase(cfg.get("access.token.claim"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSessionNoteMapper(ClientContract client, String noteKey) {
        for (ProtocolMapperContract pm : client.protocolMappers()) {
            if (!"oidc-usersessionmodel-note-mapper".equals(pm.protocolMapper())) {
                continue;
            }
            Map<String, String> cfg = pm.config();
            if (noteKey.equals(cfg.get("user.session.note"))
                    && noteKey.equals(cfg.get("claim.name"))
                    && "true".equalsIgnoreCase(cfg.get("access.token.claim"))) {
                return true;
            }
        }
        return false;
    }
}
