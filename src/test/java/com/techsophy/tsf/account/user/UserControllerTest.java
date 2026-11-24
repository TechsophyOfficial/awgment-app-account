package com.techsophy.tsf.account.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.GlobalExceptionHandler;
import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import com.techsophy.tsf.account.security.ServiceAccountPrincipalDetector;
import com.techsophy.tsf.account.tenant.dto.PageResponse;
import com.techsophy.tsf.account.user.dto.CreateUserRequest;
import com.techsophy.tsf.account.user.dto.UpdateMyProfileRequest;
import com.techsophy.tsf.account.user.dto.UpdateUserRequest;
import com.techsophy.tsf.account.user.dto.UserDeactivationResponse;
import com.techsophy.tsf.account.user.dto.UserResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc unit tests for UserController covering UC-USER-01..07
 * HTTP contracts, strict Jackson BOPLA rejection, validation, and CallerPrincipal resolution.
 */
class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        userService = mock(UserService.class);

        AwgmentSecurityProperties secProps = new AwgmentSecurityProperties();
        secProps.setKeycloakBaseUrl("http://localhost:8080");
        secProps.setPlatformRealm("platform");
        secProps.setServiceAccountClients(List.of("awgment-service-client"));
        ServiceAccountPrincipalDetector detector = new ServiceAccountPrincipalDetector(secProps);

        ObjectMapper strictMapper = Jackson2ObjectMapperBuilder.json()
                .failOnUnknownProperties(true)
                .build();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ProxyFactory proxyFactory = new ProxyFactory(new UserController(userService, detector, secProps));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
        UserController validatedController = (UserController) proxyFactory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(validatedController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(strictMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void createUserShouldReturn201WithLocationHeaderAndResolvedPrincipal() throws Exception {
        bindJwtAuth("acme", "op-sub-1", List.of("awgment-account-all"), false);

        UserResponse expected = fullResponse(2001L, "alice", "alice" + "@" + "acme.invalid", "en", UserStatus.ACTIVE);
        when(userService.createUser(any(CreateUserRequest.class), eq(new CallerPrincipal(
                "op-sub-1", "acme", Set.of("awgment-account-all"), false))))
                .thenReturn(expected);

        mockMvc.perform(post("/accounts/v2/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"alice\",\"emailId\":\"" + "alice" + "@" + "acme.invalid"
                                + "\",\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"roles\":[\"awgment-user\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/v2/users/2001"))
                .andExpect(jsonPath("$.id").value(2001L))
                .andExpect(jsonPath("$.userName").value("alice"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createUserShouldRejectMalformedPayloadAndUnknownFields() throws Exception {
        bindJwtAuth("acme", "op-sub-1", List.of("awgment-account-all"), false);

        mockMvc.perform(post("/accounts/v2/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"a\",\"emailId\":\"bad-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.userName").exists())
                .andExpect(jsonPath("$.fieldErrors.emailId").exists());

        mockMvc.perform(post("/accounts/v2/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"alice\",\"emailId\":\"" + "alice" + "@" + "acme.invalid"
                                + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMMUTABLE_FIELD_MODIFICATION"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateGetSearchReactivateAndMeEndpointsShouldMapAllParametersAndStatuses() throws Exception {
        bindJwtAuth("acme", "op-sub-1", List.of("awgment-account-all"), false);

        UserResponse updated = fullResponse(2001L, "alice", "alice" + "@" + "acme.invalid", "fr", UserStatus.ACTIVE);
        when(userService.updateUser(eq(2001L), any(UpdateUserRequest.class), any())).thenReturn(updated);

        mockMvc.perform(put("/accounts/v2/users/2001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"locale\":\"fr\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("fr"));

        UserResponse mandatoryOnly = new UserResponse(
                2001L, null, "alice", "alice" + "@" + "acme.invalid",
                "Alice", "Smith", null, null, "fr", null,
                null, null, IdentitySource.KEYCLOAK_LOCAL, null, null,
                UserStatus.ACTIVE, null, null, SyncStatus.PENDING_SYNC, 1L, null, null
        );
        when(userService.getUserById(2001L, true)).thenReturn(mandatoryOnly);

        mockMvc.perform(get("/accounts/v2/users/2001")
                        .param("onlyMandatoryFields", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2001L))
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.attributes").doesNotExist())
                .andExpect(jsonPath("$.desiredRoles").doesNotExist());

        when(userService.searchUsers(eq("ali"), eq(UserStatus.ACTIVE), eq(IdentitySource.KEYCLOAK_LOCAL),
                isNull(), eq("Engineering"), isNull(), eq(false), eq(0), eq(20), eq("createdAt,desc"), any()))
                .thenReturn(new PageResponse<>(List.of(updated), 0, 20, 1L, 1));

        mockMvc.perform(get("/accounts/v2/users")
                        .param("q", "ali")
                        .param("status", "ACTIVE")
                        .param("identitySource", "KEYCLOAK_LOCAL")
                        .param("department", "Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userName").value("alice"))
                .andExpect(jsonPath("$.totalElements").value(1));

        when(userService.reactivateUser(eq(2001L), any())).thenReturn(updated);
        mockMvc.perform(post("/accounts/v2/users/2001/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        when(userService.getMyProfile(any())).thenReturn(updated);
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("alice"));

        when(userService.updateMyProfile(any(UpdateMyProfileRequest.class), any())).thenReturn(updated);
        mockMvc.perform(put("/accounts/v2/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"fr\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("fr"));

        // Oversized search query parameters (> 255 chars) rejected with 400 VALIDATION_FAILED
        mockMvc.perform(get("/accounts/v2/users").param("q", "a".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/accounts/v2/users").param("loginId", "b".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void deactivateUserShouldReturn204WhenSyncedAnd202WhenDegraded() throws Exception {
        bindJwtAuth("acme", "op-sub-1", List.of("awgment-account-delete"), false);

        when(userService.deactivateUser(eq(2001L), any())).thenReturn(
                new UserService.DeactivationOutcome(
                        true, new UserDeactivationResponse(UserStatus.DISABLED, SyncStatus.SYNCED)));
        mockMvc.perform(delete("/accounts/v2/users/2001"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        when(userService.deactivateUser(eq(2002L), any())).thenReturn(
                new UserService.DeactivationOutcome(
                        false, new UserDeactivationResponse(UserStatus.DISABLED, SyncStatus.PENDING_SYNC)));
        mockMvc.perform(delete("/accounts/v2/users/2002"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.syncStatus").value("PENDING_SYNC"));
    }

    @Test
    void shouldFallbackGracefullyToTenantContextAndNonJwtAuthentication() throws Exception {
        TenantContext.setTenantId("beta");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "mock-operator", "n/a", List.of(new SimpleGrantedAuthority("awgment-account-read"))));
        UserResponse user = fullResponse(2003L, "zoe", "zoe" + "@" + "beta.invalid", "en", UserStatus.ACTIVE);
        when(userService.getUserById(2003L, false)).thenReturn(user);
        when(userService.getMyProfile(any())).thenReturn(user);

        mockMvc.perform(get("/accounts/v2/users/2003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("zoe"));
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isOk());
        verify(userService).getMyProfile(new CallerPrincipal(
                "mock-operator", "beta", Set.of("awgment-account-read"), false));

        // Blank auth context & null JWT
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isOk());
        verify(userService).getMyProfile(new CallerPrincipal(null, "public", Set.of(), false));

        // Blank subject JWT + blank auth name + null/blank authorities + untrusted issuer
        Jwt untrustedIssJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject(" ")
                .claim("iss", "https://attacker.invalid/realms/acme")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                untrustedIssJwt,
                List.of((org.springframework.security.core.GrantedAuthority) () -> " "),
                " "
        ));
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isOk());

        // Null issuer + invalid tenant realm ("public") + null authorities collection
        Jwt invalidRealmJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", "http://localhost:8080/realms/public")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(invalidRealmJwt, null, null) {
            @Override
            public String getName() {
                return null;
            }
        });
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isOk());

        Jwt noIssJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject("sub-no-iss")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(noIssJwt, List.of(new SimpleGrantedAuthority("awgment-user"))));
        mockMvc.perform(get("/accounts/v2/users/me"))
                .andExpect(status().isOk());
        verify(userService).getMyProfile(new CallerPrincipal("sub-no-iss", "public", Set.of("awgment-user"), false));
    }

    private static void bindJwtAuth(String realm, String sub, List<String> roles, boolean serviceAccount) {
        Jwt.Builder builder = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject(sub)
                .claim("iss", "http://localhost:8080/realms/" + realm);
        if (serviceAccount) {
            builder.claim("client_id", "awgment-service-client");
            builder.claim("azp", "awgment-service-client");
        }
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }

    private static UserResponse fullResponse(
            Long id, String userName, String emailId, String locale, UserStatus status) {
        return new UserResponse(
                id,
                "kc-" + id,
                userName,
                emailId,
                "First",
                "Last",
                "+1-555-0100",
                "Engineering",
                locale,
                "UTC",
                Map.of("theme", "dark"),
                Map.of("team", "core"),
                IdentitySource.KEYCLOAK_LOCAL,
                null,
                ProvisioningMode.DIRECT_OPERATOR,
                status,
                List.of("awgment-user"),
                List.of("eng"),
                SyncStatus.PENDING_SYNC,
                1L,
                Instant.now(),
                Instant.now()
        );
    }

    private static <T> T isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }
}
