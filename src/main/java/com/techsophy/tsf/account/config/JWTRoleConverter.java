package com.techsophy.tsf.account.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.techsophy.tsf.account.constants.AccountConstants.CLIENT_ROLES;

@Slf4j
@Component
public class JWTRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>>
{
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt)
    {
        List<String> awgmentRolesList = jwt.getClaimAsStringList(CLIENT_ROLES);
        if (awgmentRolesList == null)
        {
            log.warn("Validated JWT has no clientRoles claim");
            return Collections.emptyList();
        }
        return awgmentRolesList.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
