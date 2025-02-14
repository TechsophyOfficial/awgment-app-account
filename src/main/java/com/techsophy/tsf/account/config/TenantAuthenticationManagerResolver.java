package com.techsophy.tsf.account.config;

import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import static com.techsophy.tsf.account.constants.AccountConstants.AUTHORIZATION;
import static com.techsophy.tsf.account.constants.AccountConstants.KEYCLOAK_ISSUER_URI;

/**
 * Resolves authentication managers dynamically for multi-tenant authentication using Keycloak.
 */
@RefreshScope
@Component
@RequiredArgsConstructor
public class TenantAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest>
{
	/**
	 * Cache for storing authentication managers for different tenants.
	 */
	private final Map<String, AuthenticationManager> authenticationManagers = new HashMap<>();

	/**
	 * Keycloak issuer URI for authentication.
	 */
	@Value(KEYCLOAK_ISSUER_URI)
	private String keycloakIssuerUri;

	/**
	 * Converter to map JWT roles to Spring Security authorities.
	 */
	private final JWTRoleConverter jwtRoleConverter;

	/**
	 * Utility for extracting tenant information from JWT tokens.
	 */
	private final TokenUtils tokenUtils;

	/**
	 * Resolves an authentication manager based on the tenant derived from the request.
	 *
	 * @param request the incoming HTTP request
	 * @return the authentication manager for the resolved tenant
	 */
	@Override
	public AuthenticationManager resolve(HttpServletRequest request)
	{
		return this.authenticationManagers.computeIfAbsent(toTenant(request), this::fromTenant);
	}

	/**
	 * Extracts the tenant identifier from the request's authorization header.
	 *
	 * @param request the incoming HTTP request
	 * @return the tenant identifier extracted from the token
	 */
	private String toTenant(HttpServletRequest request)
	{
		try
		{
			return tokenUtils.getIssuerFromToken(request.getHeader(AUTHORIZATION));
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Creates an authentication manager for a specific tenant using Keycloak.
	 *
	 * @param tenant the tenant identifier
	 * @return the authentication manager for the given tenant
	 */
	private AuthenticationManager fromTenant(String tenant)
	{
		JwtAuthenticationProvider jwtAuthenticationProvider =
				new JwtAuthenticationProvider(JwtDecoders.fromIssuerLocation(keycloakIssuerUri + tenant));
		jwtAuthenticationProvider.setJwtAuthenticationConverter(authenticationConverter());
		return jwtAuthenticationProvider::authenticate;
	}

	/**
	 * Configures the JWT authentication converter to use the custom role converter.
	 *
	 * @return a configured {@link JwtAuthenticationConverter} instance
	 */
	private JwtAuthenticationConverter authenticationConverter()
	{
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwtRoleConverter);
		return converter;
	}
}
