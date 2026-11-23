package com.techsophy.tsf.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.time.Instant;

/**
 * Phase 0 baseline security configuration enforcing stateless sessions,
 * probe-only public actuator endpoints, and RFC 7807 401 responses.
 * Phase 1 extends this chain with multi-tenant JWT resolution and audience validation.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BootstrapSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${awgment.security.dev-docs-enabled:false}") boolean devDocsEnabled) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll();
                    if (devDocsEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problemDetailEntryPoint(objectMapper)));

        return http.build();
    }

    private AuthenticationEntryPoint problemDetailEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    ApiErrorCode.INVALID_TOKEN.status(),
                    "Authentication is required to access this resource."
            );
            problem.setType(ApiErrorCode.INVALID_TOKEN.type());
            problem.setTitle(ApiErrorCode.INVALID_TOKEN.title());
            problem.setInstance(URI.create(request.getRequestURI()));
            problem.setProperty("errorCode", ApiErrorCode.INVALID_TOKEN.name());
            problem.setProperty("timestamp", Instant.now().toString());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), problem);
        };
    }
}
