package com.techsophy.tsf.account.security;

import com.techsophy.tsf.account.multitenancy.TenantContext;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Binds {@link TenantContext} from a verified tenant {@link JwtAuthenticationToken} inside
 * the Spring Security chain and unconditionally clears it in a {@code finally} block.
 * Non-JWT authentications (e.g., {@code @WithMockUser}) and Platform Control Realm tokens
 * retain {@link TenantContext#DEFAULT_TENANT}.
 */
@Component
public class TenantContextCleanupFilter extends OncePerRequestFilter {

    private final AwgmentSecurityProperties properties;
    private final KeycloakIssuerParser issuerParser;

    public TenantContextCleanupFilter(AwgmentSecurityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.issuerParser = new KeycloakIssuerParser(this.properties);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            bindTenantContextIfApplicable();
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void bindTenantContextIfApplicable() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            TenantContext.clear();
            return;
        }
        Jwt jwt = jwtAuth.getToken();
        String issuer = jwt.getIssuer() != null
                ? jwt.getIssuer().toString()
                : jwt.getClaimAsString(JwtClaimNames.ISS);
        issuerParser.extractRealm(issuer)
                .filter(realm -> !realm.equals(properties.getPlatformRealm()) && TenantIdValidator.isValid(realm))
                .ifPresentOrElse(TenantContext::setTenantId, TenantContext::clear);
    }
}
