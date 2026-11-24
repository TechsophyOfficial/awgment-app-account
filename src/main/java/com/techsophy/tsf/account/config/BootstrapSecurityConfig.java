package com.techsophy.tsf.account.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import com.techsophy.tsf.account.security.AwgmentSecurityProperties;
import com.techsophy.tsf.account.security.JsonRequestSizeLimitFilter;
import com.techsophy.tsf.account.security.TenantAccessAuthenticationException;
import com.techsophy.tsf.account.security.TenantAuthenticationManagerResolver;
import com.techsophy.tsf.account.security.TenantContextCleanupFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Map;

/**
 * Security configuration enforcing stateless sessions, OWASP security headers,
 * GET/HEAD-only public Actuator probes, dev-only Swagger chain, RFC 7807 ProblemDetail
 * responses, multi-tenant JWT resolution with SSRF protection, and TenantContext cleanup.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AwgmentSecurityProperties.class)
public class BootstrapSecurityConfig {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
    private static final String SWAGGER_CSP =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'";
    private static final String PERMISSIONS_POLICY = "geolocation=(), microphone=(), camera=()";

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "awgment.security.dev-docs-enabled", havingValue = "true")
    public SecurityFilterChain devDocsSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(SWAGGER_CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(p -> p.policy(PERMISSIONS_POLICY))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailEntryPoint(objectMapper))
                        .accessDeniedHandler(problemDetailAccessDeniedHandler(objectMapper))
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            TenantAuthenticationManagerResolver tenantAuthenticationManagerResolver,
            JsonRequestSizeLimitFilter jsonRequestSizeLimitFilter,
            TenantContextCleanupFilter tenantContextCleanupFilter) throws Exception {
        AuthenticationEntryPoint entryPoint = problemDetailEntryPoint(objectMapper);
        AccessDeniedHandler deniedHandler = problemDetailAccessDeniedHandler(objectMapper);

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(p -> p.policy(PERMISSIONS_POLICY))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> {
                    oauth2.authenticationManagerResolver(tenantAuthenticationManagerResolver)
                            .authenticationEntryPoint(entryPoint)
                            .accessDeniedHandler(deniedHandler);
                    oauth2.withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                        @Override
                        public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                            filter.setAuthenticationFailureHandler(entryPoint::commence);
                            return filter;
                        }
                    });
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )
                .addFilterBefore(jsonRequestSizeLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantContextCleanupFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JsonRequestSizeLimitFilter> jsonRequestSizeLimitFilterRegistration(
            JsonRequestSizeLimitFilter filter) {
        FilterRegistrationBean<JsonRequestSizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer awgmentJacksonStreamConstraintsCustomizer(
            AwgmentSecurityProperties securityProperties) {
        long maxDocBytes = securityProperties.effectiveMaxJsonBodyBytes();
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxNestingDepth(1000)
                        .maxDocumentLength(maxDocBytes)
                        .maxStringLength(64 * 1024)
                        .maxNameLength(1024)
                        .build()
        ));
    }

    @Bean
    public FilterRegistrationBean<TenantContextCleanupFilter> tenantContextCleanupFilterRegistration(
            TenantContextCleanupFilter filter) {
        FilterRegistrationBean<TenantContextCleanupFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public RequestRejectedHandler requestRejectedHandler(ObjectMapper objectMapper) {
        return (request, response, ex) -> ProblemDetailFactory.writeServletProblem(
                objectMapper,
                request,
                response,
                ApiErrorCode.VALIDATION_FAILED,
                "Request rejected due to malformed URL or forbidden characters.",
                Map.of(
                        "X-Content-Type-Options", "nosniff",
                        "X-Frame-Options", "DENY",
                        "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate",
                        "Content-Security-Policy", API_CSP,
                        "Referrer-Policy", "no-referrer",
                        "Permissions-Policy", PERMISSIONS_POLICY
                )
        );
    }

    private AuthenticationEntryPoint problemDetailEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            if (authException instanceof TenantAccessAuthenticationException tenantEx) {
                ProblemDetailFactory.writeServletProblem(
                        objectMapper,
                        request,
                        response,
                        tenantEx.errorCode(),
                        tenantEx.getMessage(),
                        Map.of()
                );
                return;
            }
            if (authException instanceof AuthenticationServiceException) {
                ProblemDetailFactory.writeServletProblem(
                        objectMapper,
                        request,
                        response,
                        ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                        "Identity provider is temporarily unavailable.",
                        Map.of()
                );
                return;
            }
            ProblemDetailFactory.writeServletProblem(
                    objectMapper,
                    request,
                    response,
                    ApiErrorCode.INVALID_TOKEN,
                    "Authentication is required to access this resource.",
                    Map.of(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            );
        };
    }

    AccessDeniedHandler problemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> ProblemDetailFactory.writeServletProblem(
                objectMapper,
                request,
                response,
                ApiErrorCode.ACCESS_DENIED,
                "You do not have sufficient authority to perform this operation.",
                Map.of()
        );
    }
}
