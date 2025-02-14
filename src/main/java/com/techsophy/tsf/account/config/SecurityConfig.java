package com.techsophy.tsf.account.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletRequest;

import static com.techsophy.tsf.account.constants.PropertyConstant.INTERNAL_ANT_MATCHER;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(INTERNAL_ANT_MATCHER).permitAll()  // Allow internal routes
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.authenticationManagerResolver(authenticationManagerResolver)
                );

        return http.build();
    }
}
