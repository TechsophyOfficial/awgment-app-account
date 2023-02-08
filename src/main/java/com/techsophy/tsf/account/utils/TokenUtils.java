package com.techsophy.tsf.account.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.exception.InvalidInputException;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class TokenUtils
{
    GlobalMessageSource globalMessageSource;
    @Value(KEYCLOAK_ISSUER_URI)
    private final String keyCloakApi;

    @Value(DEFAULT_PAGE_LIMIT)
    private Integer defaultPageLimit;

    ObjectMapper objectMapper;
    WebClientWrapper webClientWrapper;

    private static final Logger logger = LoggerFactory.getLogger(TokenUtils.class);

    public String getLoggedInUserName()
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
        var client = webClientWrapper.createWebClient(token);
        String userInfoResponse = webClientWrapper.webclientRequest(client,keyCloakApi+getIssuerFromToken(token)+USER_INFO_URL,GET,null);
        if(userInfoResponse.isEmpty())
        {
            logger.info(TOKEN_VERIFICATION_FAILED);
            throw new AccessDeniedException(TOKEN_VERIFICATION_FAILED);
        }
        Map<String,Object> userInformationMap=this.objectMapper.readValue(userInfoResponse,Map.class);
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
}
