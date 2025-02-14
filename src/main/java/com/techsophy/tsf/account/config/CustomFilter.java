package com.techsophy.tsf.account.config;

import com.techsophy.multitenancy.mongo.config.TenantContext;
import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import static com.techsophy.tsf.account.constants.AccountConstants.AUTHORIZATION;
import static com.techsophy.tsf.account.constants.AccountConstants.TENANT_NOT_FOUND;
import static com.techsophy.tsf.account.constants.PropertyConstant.INTERNAL_URL;

/**
 * Custom filter for handling multi-tenancy.
 * This filter extracts the tenant ID from the Authorization token and sets it in the TenantContext.
 */
@Configuration
@RequiredArgsConstructor
public class CustomFilter implements Filter {

	private final TokenUtils tokenUtils;

	/**
	 * Filters incoming HTTP requests to extract the tenant ID from the Authorization header.
	 * If the request URL contains an internal API path, it is ignored.
	 * If the tenant ID is found, it is set in the TenantContext; otherwise, an exception is thrown.
	 *
	 * @param request  the incoming servlet request
	 * @param response the servlet response
	 * @param chain    the filter chain
	 * @throws IllegalStateException if the tenant ID is not found
	 */
	@SneakyThrows
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		if (!httpRequest.getRequestURL().toString().contains(INTERNAL_URL)) {
			String tenant = tokenUtils.getIssuerFromToken(httpRequest.getHeader(AUTHORIZATION));
			if (StringUtils.isNotEmpty(tenant)) {
				TenantContext.setTenantId(tenant);
			} else {
				throw new IllegalStateException(TENANT_NOT_FOUND);
			}
		}
		chain.doFilter(request, response);
	}
}
