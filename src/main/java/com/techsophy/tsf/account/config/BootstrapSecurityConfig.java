package com.techsophy.tsf.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.error.ProblemDetailFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Map;

/**
 * Phase 0 baseline security configuration enforcing stateless sessions,
 * OWASP security headers, GET/HEAD-only public Actuator probes, dev-only Swagger chain,
 * and RFC 7807 ProblemDetail responses across 400/401/403 filter errors.
 * Phase 1 extends the API chain with multi-tenant JWT resolution and audience validation.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BootstrapSecurityConfig {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
    private static final String SWAGGER_CSP =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'";
    private static final String PERMISSIONS_POLICY = "geolocation=(), microphone=(), camera=()";

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "awgment.security.dev-docs-enabled", havingValue = "true")
    public SecurityFilterChain devDocsSecurityFilterChain(HttpSecurity http) throws Exception {
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
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
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
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailEntryPoint(objectMapper))
                        .accessDeniedHandler(problemDetailAccessDeniedHandler(objectMapper))
                );

        return http.build();
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
                        "Referrer-Policy", "no-referrer"
                )
        );
    }

    private AuthenticationEntryPoint problemDetailEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> ProblemDetailFactory.writeServletProblem(
                objectMapper,
                request,
                response,
                ApiErrorCode.INVALID_TOKEN,
                "Authentication is required to access this resource.",
                Map.of(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        );
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
