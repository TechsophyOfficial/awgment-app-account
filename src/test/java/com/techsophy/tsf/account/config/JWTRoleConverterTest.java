package com.techsophy.tsf.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collections;

import static com.techsophy.tsf.account.constants.AccountConstants.CLIENT_ROLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JWTRoleConverterTest
{
    private final JWTRoleConverter converter = new JWTRoleConverter();

    @Test
    void grantsAuthoritiesFromValidatedJwtWithoutUserInfoRequest()
    {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsStringList(CLIENT_ROLES)).thenReturn(Arrays.asList("awgment-account-all", "awgment-account-read"));

        assertThat(converter.convert(jwt))
                .extracting("authority")
                .containsExactly("awgment-account-all", "awgment-account-read");
    }

    @Test
    void missingClientRolesGrantsNoAuthorities()
    {
        Jwt jwt = mock(Jwt.class);

        assertThat(converter.convert(jwt)).isEqualTo(Collections.emptyList());
    }
}
