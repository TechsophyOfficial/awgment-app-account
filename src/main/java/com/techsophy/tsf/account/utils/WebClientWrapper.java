package com.techsophy.tsf.account.utils;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@Service
public class WebClientWrapper
{
    public WebClient createWebClient(String token)
    {
        return   WebClient.builder()
                .defaultHeader(AUTHORIZATION,BEARER+token)
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON)
                .build();
    }

    public String webclientRequest(WebClient client, String url, @NotBlank String requestType, Object data)
    {
        if(requestType.equalsIgnoreCase(GET))
        {
            return Objects.requireNonNull(client.get()
                            .uri(url)
                            .retrieve())
                    .bodyToMono(String.class)
                    .block();
        }
        else
        {
            if(requestType.equalsIgnoreCase(DELETE))
            {
                if (data == null)
                {
                    return Objects.requireNonNull(client.method(HttpMethod.DELETE)
                                    .uri(url)
                                    .retrieve())
                            .bodyToMono(String.class)
                            .block();
                }
                else
                {
                    return Objects.requireNonNull(client.method(HttpMethod.DELETE)
                                    .uri(url)
                                    .bodyValue(data)
                                    .retrieve())
                            .bodyToMono(String.class)
                            .block();
                }
            }
            if(requestType.equalsIgnoreCase(PUT))
            {
                return Objects.requireNonNull(client.put()
                                .uri(url)
                                .bodyValue(data)
                                .retrieve())
                        .bodyToMono(String.class)
                        .block();
            }
            return Objects.requireNonNull(client.post()
                            .uri(url)
                            .bodyValue(data)
                            .retrieve())
                    .bodyToMono(String.class)
                    .block();
        }
    }
    public String webclientRequestForUser(WebClient client, String url, String userName)
    {
            return Objects.requireNonNull(client.get()
                            .uri(url,uriBuilder-> uriBuilder.queryParam(BRIEF_REPRESENTATION,true).queryParam(EXACT,true).queryParam(FIRST,0).queryParam(MAX,1).queryParam(SEARCH,userName).build())
                            .retrieve())
                    .bodyToMono(String.class)
                    .block();
    }

    }

