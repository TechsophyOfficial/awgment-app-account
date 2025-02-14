package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.util.*;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.INVALID_PAGE_REVIEW;
import static com.techsophy.tsf.account.constants.ErrorConstants.INVALID_TOKEN;

@RefreshScope
@Service
@RequiredArgsConstructor
public class TokenUtils
{
    GlobalMessageSource globalMessageSource;
    @Value(KEYCLOAK_ISSUER_URI)
    private final String keyCloakApi;
    private final TokenUtils tokenUtility;
    @Value(DEFAULT_PAGE_LIMIT)
    private Integer defaultPageLimit;

    final ObjectMapper objectMapper;
    final WebClientWrapper webClientWrapper;

    private static final Logger logger = LoggerFactory.getLogger(TokenUtils.class);

    public String getLoggedInUserId()
    {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null)
        {
            Authentication authentication = context.getAuthentication();
            if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
            {
                Object principal = authentication.getPrincipal();
                if (principal instanceof OAuth2User)
                {
                    return Optional.of(((OAuth2User) principal).getName()).filter(StringUtils::isNotEmpty).orElseThrow(RuntimeException::new);
                }
                else if (principal instanceof Jwt)
                {
                    Jwt jwt = (Jwt) principal;
                    return jwt.getClaim(PREFERED_USERNAME);
                }
                else
                {
                    throw new SecurityException(AUTHENTICATION_FAILED);
                }
            }
            else
            {
                throw new SecurityException(AUTHENTICATION_FAILED);
            }
        }
        else
        {
            throw new SecurityException(AUTHENTICATION_FAILED);
        }
    }

    public String getIssuerFromContext()
    {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null)
        {
            Authentication authentication = context.getAuthentication();
            if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
            {
                Object principal = authentication.getPrincipal();
                if (principal instanceof OAuth2User)
                {
                    return Optional.of(((OAuth2User) principal).getName()).filter(StringUtils::isNotEmpty).orElseThrow(RuntimeException::new);
                }
                else if (principal instanceof Jwt)
                {
                    Jwt jwt = (Jwt) principal;
                    List<String> issuerUrl= Arrays.asList(jwt.getClaim(ISS).toString().split(URL_SEPERATOR));
                    return issuerUrl.get(issuerUrl.size()-1);
                }
                else
                {
                    throw new SecurityException(AUTHENTICATION_FAILED);
                }
            }
            else
            {
                throw new SecurityException(AUTHENTICATION_FAILED);
            }
        }
        else
        {
            throw new SecurityException(AUTHENTICATION_FAILED);
        }
    }
   @SneakyThrows
   public String getIssuerFromToken(String idToken)
   {
       String tenantName = EMPTY_STRING;
       //idToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI3Y2JZYmFtdV9kRUlMaE1FZFM1OE5vdkJlMmJyd2RXWGw3ZERXRmhBQTQ0In0.eyJleHAiOjE3MzY1MDk2NTUsImlhdCI6MTczNjUwOTM1NSwianRpIjoiMDUyZDkxNDgtYTMxNC00NTUyLWIwNWQtMmRjZjQ2MjkwOThlIiwiaXNzIjoiaHR0cHM6Ly9hdXRoLW10LW5ldy50cm92aXR5LmNvbS9hdXRoL3JlYWxtcy90cm92aXR5IiwiYXVkIjpbInJlYWxtLW1hbmFnZW1lbnQiLCJhY2NvdW50Il0sInN1YiI6Ijk1Y2EzMzQzLTg1ZDctNDBmMi04ZjYxLTJlOTUyNDJjNTNhMyIsInR5cCI6IkJlYXJlciIsImF6cCI6InRyb3ZpdHktYmFja2VuZCIsInNpZCI6ImRkNDczM2UwLWFhYTktNDQ0NS04YzlkLThiNjg3ZjZjMzE4YiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtdHJvdml0eSIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJ0cm92aXR5LWJhY2tlbmQiOnsicm9sZXMiOlsiY2hhbm5lbC1jb25maWctdmlldyIsImF3Z21lbnQtdXRpbC1hbGwiLCJhd2dtZW50LWRtcy1hbGwiLCJjaGFubmVsLWNvbmZpZy1jcmVhdGUiLCJhd2dtZW50LWRtcy1jcmVhdGUtb3ItdXBkYXRlIiwiY29tcG9uZW50cy1jcmVhdGUiLCJhd2dtZW50LXdvcmtmbG93LWFsbCIsImF3Z21lbnQtYWNjb3VudC1hbGwiLCJjb21wb25lbnRzLXZpZXciXX0sInJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmlldy1yZWFsbSIsInZpZXctaWRlbnRpdHktcHJvdmlkZXJzIiwibWFuYWdlLWlkZW50aXR5LXByb3ZpZGVycyIsImNyZWF0ZS1jbGllbnQiLCJtYW5hZ2UtdXNlcnMiLCJxdWVyeS1yZWFsbXMiLCJ2aWV3LWF1dGhvcml6YXRpb24iLCJxdWVyeS1jbGllbnRzIiwicXVlcnktdXNlcnMiLCJtYW5hZ2UtZXZlbnRzIiwibWFuYWdlLXJlYWxtIiwidmlldy1ldmVudHMiLCJ2aWV3LXVzZXJzIiwidmlldy1jbGllbnRzIiwibWFuYWdlLWF1dGhvcml6YXRpb24iLCJtYW5hZ2UtY2xpZW50cyIsInF1ZXJ5LWdyb3VwcyJdfX0sInNjb3BlIjoiYXdnbWVudCBlbWFpbCBwcm9maWxlIG9wZW5pZCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwibmFtZSI6IlZpbmF5IEt1bWFyIFRhbW1pc2hldHR5IiwicHJlZmVycmVkX3VzZXJuYW1lIjoiNzY3NDk3NzUyMyIsImdpdmVuX25hbWUiOiJWaW5heSBLdW1hciIsInVzZXJJZCI6Ijc2NzQ5Nzc1MjMiLCJmYW1pbHlfbmFtZSI6IlRhbW1pc2hldHR5IiwiZW1haWwiOiJ2aW5heS50QHRlY2hzb3BoeS5jb20iLCJjbGllbnRSb2xlcyI6WyJjaGFubmVsLWNvbmZpZy12aWV3IiwiYXdnbWVudC11dGlsLWFsbCIsImF3Z21lbnQtZG1zLWFsbCIsImNoYW5uZWwtY29uZmlnLWNyZWF0ZSIsImF3Z21lbnQtZG1zLWNyZWF0ZS1vci11cGRhdGUiLCJjb21wb25lbnRzLWNyZWF0ZSIsImF3Z21lbnQtd29ya2Zsb3ctYWxsIiwiYXdnbWVudC1hY2NvdW50LWFsbCIsImNvbXBvbmVudHMtdmlldyJdfQ.IqTIlvxmoXQC4M3QQ76tt35umFCc4D_9CPUbR29IsNdD1-TWBUUIP9IyEd-oVjUMZIaggTsziwFxSUwE9cZTtM196f2gRK_WZJlH1Y5dilrfMsnQm-fOLcVp2kPgGgbDRlE9_Nvpe7kMpYevjCIBMWdru0Sw6Khpzxd04lkDYWjkiw6jJdIMTOJa32WRqiShL9zPgGvRtgRdE88ESJyQQgPKhDSDwl9zZNW4yolIChYqitKlz_3OZfGNYjUOn8d5RWE1DuUc40oL5ebDflZGEdz1CqWyHak7Lo9PmULwYycl0Gx3lhCjumYRX-HhlnbV7NJpMWij5oWhdMV6Q1DM2g";
       final Base64.Decoder decoder = Base64.getDecoder();
       if (idToken.startsWith(BEARER))
       {
           idToken=idToken.substring(SEVEN);
       }
       Map<String, Object> tokenBody = new HashMap<>();
       List<String> tokenizer = Arrays.asList(idToken.split(REGEX_SPLIT));
       for(String token:tokenizer)
       {
           if(token.equals(tokenizer.get(ONE)))
           {
               tokenBody=string2JSONMap(new String(decoder.decode(token)));
           }
       }
       if( tokenBody == null )
       {
           throw new InvalidInputException(INVALID_TOKEN,globalMessageSource.get(INVALID_TOKEN));
       }
       if(tokenBody.containsKey(ISS))
       {
           List<String> elements= Arrays.asList(tokenBody.get(ISS).toString().split(URL_SEPERATOR));
           tenantName=elements.get(elements.size()-1);
       }
       return tenantName;
   }

    public String getTokenFromContext()
    {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null)
        {
            Authentication authentication = context.getAuthentication();
            if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
            {
                Object principal = authentication.getPrincipal();
                if (principal instanceof OAuth2User)
                {
                    return Optional.of(((OAuth2User) principal).getName()).filter(StringUtils::isNotEmpty).orElseThrow(RuntimeException::new);
                }
                else if (principal instanceof Jwt)
                {
                    Jwt jwt = (Jwt) principal;

                    return jwt.getTokenValue();
                }
                else
                {
                    throw new SecurityException(UNABLE_GET_TOKEN);
                }
            }
            else
            {
                throw new SecurityException(UNABLE_GET_TOKEN);
            }
        }
        else
        {
            throw new SecurityException(UNABLE_GET_TOKEN);
        }
    }

    @SneakyThrows
    public Map<String, Object> string2JSONMap(String json)
    {
        ObjectMapper mapper = new ObjectMapper();
        // convert JSON string to Map
        return mapper.readValue(json, new TypeReference<>(){});
    }

    public PageRequest getPageRequest(Integer page, Integer pageSize, String[] sortByArray)
    {
        if (page != null && page > 0)
        {
            if (pageSize == null || pageSize <= 0)
            {
                pageSize = this.defaultPageLimit;
            }
            int pageNo = page - 1;
            return PageRequest.of(pageNo, pageSize, getSortBy(sortByArray));
        }
        else
        {
            throw new InvalidInputException(INVALID_PAGE_REVIEW,globalMessageSource.get(INVALID_PAGE_REVIEW));
        }
    }

    public <T> PaginationResponsePayload getPaginationResponsePayload(Page<T> page, List<Map<String,Object>> content)
    {
        PaginationResponsePayload paginationResponsePayload = new PaginationResponsePayload();
        paginationResponsePayload.setTotalPages(page.getTotalPages());
        paginationResponsePayload.setTotalElements(page.getTotalElements());
        paginationResponsePayload.setPage(page.getNumber() + 1);
        paginationResponsePayload.setSize(page.getSize());
        paginationResponsePayload.setNumberOfElements(page.getNumberOfElements());
        paginationResponsePayload.setContent((content.isEmpty())?this.objectMapper.convertValue(page.getContent(), new TypeReference<>() {
        }):content);
        return paginationResponsePayload;
    }

    public Sort getSortBy(String[] sortByArray)
    {
        List<String> sortByList = new ArrayList<>();
        if (sortByArray != null)
        {
            sortByList = Arrays.asList(sortByArray);
        }
        Sort sort = Sort.unsorted();
        if (sortByList.isEmpty())
        {
            sort = Sort.by(CREATED_ON).descending();
        }
        else
        {
            for (String item : sortByList)
            {
                String[] itemArray = item.split(COLON);
                if (org.springframework.util.StringUtils.hasText(itemArray[0]))
                {
                    Sort itemSort = Sort.by(itemArray[0]);

                    if (itemArray.length > 1 && org.springframework.util.StringUtils.hasText(itemArray[1]) &&
                            itemArray[1].equalsIgnoreCase(DESCENDING))
                    {
                        itemSort = itemSort.descending();
                    }
                    sort = sort.and(itemSort);
                }
            }
            sort = sort.and(Sort.by(CREATED_ON).descending());
        }
        return sort;
    }

    @SneakyThrows
    public List<String> getClientRoles(String token)
    {
        List<String> totalList = null;
        Map<String, Object> userInformationMap = getUserInformationMap(token);
        if(userInformationMap.containsKey(CLIENT_ROLES))
        {
            totalList = this.objectMapper.convertValue(userInformationMap.get(CLIENT_ROLES), List.class);
            if(totalList.isEmpty())
            {
                logger.error(AWGMENT_ROLES_MISSING_IN_CLIENT_ROLES);
            }
        }
        else
        {
            logger.error(CLIENT_ROLES_MISSING_IN_USER_INFORMATION);
        }
        return totalList;
    }

    public Map<String, Object> getUserInformationMap(String token) throws AccessDeniedException, JsonProcessingException
    {
        var client = webClientWrapper.createWebClient(token);
        String userInfoResponse = webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtility.getIssuerFromToken(token)+USER_INFO_URL,GET,null);
        if(userInfoResponse.isEmpty())
        {
            logger.info(TOKEN_VERIFICATION_FAILED);
            throw new AccessDeniedException(TOKEN_VERIFICATION_FAILED);
        }
        return this.objectMapper.readValue(userInfoResponse,Map.class);
    }
}
