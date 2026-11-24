package com.techsophy.tsf.account.keycloak;

/**
 * Port for inspecting and toggling tenant realm state in Keycloak 26.
 */
public interface KeycloakRealmClient {

    KeycloakRealmContractSnapshot fetchRealmContract(String realmName);

    void setRealmEnabled(String realmName, boolean enabled);
}
