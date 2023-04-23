package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.exception.InvalidInputException;
import io.micrometer.core.instrument.util.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.techsophy.tsf.account.constants.ThemesConstants.TECHSOPHY_PLATFORM;
import static com.techsophy.tsf.account.constants.ThemesConstants.TOKEN_TXT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUtilsTest
{
    @Mock
    SecurityContext securityContext;
    @Mock
    SecurityContextHolder securityContextHolder;
    @InjectMocks
    TokenUtils tokenUtils;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    WebClientWrapper mockWebClientWrapper;
    @Mock
    ObjectMapper mockObjectMapper;

    @Test
    void getTokenFromIssuerTest() throws Exception {
        InputStream resource = new ClassPathResource(TOKEN_TXT_PATH).getInputStream();
        String result = IOUtils.toString(resource, StandardCharsets.UTF_8);
        String tenant = tokenUtils.getIssuerFromToken(result);
        assertThat(tenant).isEqualTo(TECHSOPHY_PLATFORM);
    }

    @Test
    void getPageRequestWithPageTest() {
        assertTrue(true);
    }

    @Test
    void getPageRequestInvalidInputException()
    {
        Assertions.assertThrows(InvalidInputException.class, () ->
                tokenUtils.getPageRequest(null,null,null));
    }

    @Test
    void getTokenFromContext() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        Jwt jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        String token = tokenUtils.getTokenFromContext();
        assertThat(token).isNull();
    }

    @Test
    void getTokenFromContextException() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> tokenUtils.getTokenFromContext());
    }

    @Test
    void getLoggedInUserIdTest() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> tokenUtils.getLoggedInUserId());
    }

    @Test
    void getIssuerFromContext() {
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> tokenUtils.getIssuerFromContext());
    }

    @Test
    void getPaginationResponsePayload() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("abc", "abc");
        list.add(map);
        Pageable pageable = PageRequest.of(1, 1);
        Page page = new PageImpl(list, pageable, 1L);
        PaginationResponsePayload responsePayload = tokenUtils.getPaginationResponsePayload(page, list);
        Assertions.assertNotNull(responsePayload);
    }

    @Test
    void getSortByTest() {
        String[] sortByArray = {"name", "id"};
        Sort sort = tokenUtils.getSortBy(sortByArray);
        Assertions.assertNotNull(sort);
    }
}
