package com.techsophy.tsf.account.user;

import com.techsophy.tsf.account.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA specification tests on H2 verifying indexed search filters, LIKE wildcard escaping,
 * and UC-USER-03 system user visibility rules.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserSpecificationsTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        userRepository.deleteAll();

        userRepository.save(buildUser(
                1L,
                "system",
                "system" + "@" + "acme.invalid",
                "System",
                "User",
                "Platform",
                IdentitySource.KEYCLOAK_LOCAL,
                null,
                UserStatus.ACTIVE
        ));
        userRepository.save(buildUser(
                1001L,
                "alice.smith",
                "alice.smith" + "@" + "acme.invalid",
                "Alice",
                "Smith",
                "Engineering",
                IdentitySource.KEYCLOAK_LOCAL,
                null,
                UserStatus.ACTIVE
        ));
        userRepository.save(buildUser(
                1002L,
                "bob.jones",
                "bob.jones" + "@" + "partner.invalid",
                "Bob",
                "Jones",
                "Finance",
                IdentitySource.EXTERNAL_IDP,
                "entra-acme",
                UserStatus.DISABLED
        ));
        userRepository.save(buildUser(
                1003L,
                "charlie_50pct",
                "charlie" + "@" + "acme.invalid",
                "Charlie",
                "Admin",
                "Engineering",
                IdentitySource.KEYCLOAK_LOCAL,
                null,
                UserStatus.PENDING_APPROVAL
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldExcludeSystemUserByDefaultEvenWhenQueryMatchesPrefix() {
        Page<UserEntity> result = userRepository.findAll(
                UserSpecifications.buildSpecification("sys", null, null, null, null, null, false),
                PageRequest.of(0, 20, Sort.by("userName"))
        );

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void shouldIncludeSystemUserForVerifiedServiceAccountOrExplicitLoginId() {
        List<UserEntity> serviceVisible = userRepository.findAll(
                UserSpecifications.buildSpecification("sys", null, null, null, null, null, true)
        );
        assertThat(serviceVisible).extracting(UserEntity::getUserName).containsExactly("system");

        List<UserEntity> explicitLogin = userRepository.findAll(
                UserSpecifications.buildSpecification(null, null, null, null, null, " SYSTEM ", false)
        );
        assertThat(explicitLogin).extracting(UserEntity::getUserName).containsExactly("system");
    }

    @Test
    void shouldEscapeLikeWildcardsAndFilterByAllIndexedPredicates() {
        // Wildcard '%' must not match all users
        List<UserEntity> percentMatch = userRepository.findAll(
                UserSpecifications.buildSpecification("%", null, null, null, null, null, false)
        );
        assertThat(percentMatch).isEmpty();

        // Prefix match across firstName/lastName/userName/emailId (case-insensitive)
        List<UserEntity> aliceByFirst = userRepository.findAll(
                UserSpecifications.buildSpecification("ALI", null, null, null, null, null, false)
        );
        assertThat(aliceByFirst).extracting(UserEntity::getId).containsExactly(1001L);

        // Filter by status, identitySource, idpAlias, department
        List<UserEntity> externalDisabled = userRepository.findAll(
                UserSpecifications.buildSpecification(
                        null,
                        UserStatus.DISABLED,
                        IdentitySource.EXTERNAL_IDP,
                        "entra-acme",
                        "Finance",
                        null,
                        false
                )
        );
        assertThat(externalDisabled).extracting(UserEntity::getId).containsExactly(1002L);

        // Exact case-insensitive loginId match on emailId
        List<UserEntity> byEmailLogin = userRepository.findAll(
                UserSpecifications.buildSpecification(
                        null, null, null, null, null,
                        "ALICE.SMITH" + "@" + "ACME.INVALID", false
                )
        );
        assertThat(byEmailLogin).extracting(UserEntity::getId).containsExactly(1001L);
    }

    private static UserEntity buildUser(
            Long id,
            String userName,
            String emailId,
            String firstName,
            String lastName,
            String department,
            IdentitySource identitySource,
            String idpAlias,
            UserStatus status) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUserName(userName);
        entity.setEmailId(emailId);
        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setDepartment(department);
        entity.setLocale("en");
        entity.setSelfServicePreferences(new LinkedHashMap<>());
        entity.setAttributes(new LinkedHashMap<>());
        entity.setIdentitySource(identitySource);
        entity.setIdpAlias(idpAlias);
        entity.setProvisioningMode(ProvisioningMode.DIRECT_OPERATOR);
        entity.setStatus(status);
        entity.setSyncVersion(1L);
        entity.setSyncedVersion(0L);
        entity.setSyncStatus(SyncStatus.PENDING_SYNC);
        return entity;
    }
}
