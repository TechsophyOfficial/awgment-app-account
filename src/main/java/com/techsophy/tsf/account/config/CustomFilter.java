package com.techsophy.tsf.account.config;

import com.techsophy.multitenancy.mongo.config.TenantContext;
import com.techsophy.tsf.account.utils.TokenUtils;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import static com.techsophy.tsf.account.constants.AccountConstants.AUTHORIZATION;

@Configuration
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class CustomFilter implements Filter
{
	private TokenUtils tokenUtils;

	@SneakyThrows
	@Override
	public void doFilter(ServletRequest request, ServletResponse response,FilterChain chain)
	{
	    HttpServletRequest httpRequest=(HttpServletRequest) request;
		String tenant= tokenUtils.getIssuerFromToken(httpRequest.getHeader(AUTHORIZATION));
	    if(StringUtils.isNotEmpty(tenant))
	    {
            TenantContext.setTenantId(tenant);
        }
		chain.doFilter(request, response);
	}
}