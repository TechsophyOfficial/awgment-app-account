package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.exception.ThemesNotFoundByIdException;
import com.techsophy.tsf.account.exception.UserDetailsIdNotFoundException;
import com.techsophy.tsf.account.repository.ThemesDefinitionRepository;
import com.techsophy.tsf.account.service.ThemesService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@RefreshScope
@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class ThemesServiceImplementation implements ThemesService
{
    private final GlobalMessageSource globalMessageSource;
    private final ThemesDefinitionRepository themesDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final IdGeneratorImpl idGenerator;
    private final WebClientWrapper webClientWrapper;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;

    @Value(GATEWAY_URI)
    private final String gatewayURL;

    @Override
    public ThemesResponse saveThemesData(ThemesSchema themesSchema) throws JsonProcessingException
    {
        Map<String,Object> loggedInUserDetails =userDetails.getUserDetails().get(0);
        if (StringUtils.isEmpty(loggedInUserDetails.get(ID).toString()))
        {
            throw new UserDetailsIdNotFoundException(LOGGED_IN_USER_ID_NOT_FOUND,globalMessageSource.get(LOGGED_IN_USER_ID_NOT_FOUND,loggedInUserDetails.get(ID).toString()));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(loggedInUserDetails.get(ID).toString()));
        ThemesDefinition themesDefinition=new ThemesDefinition();
        themesDefinition.setName(themesSchema.getName());
        themesDefinition.setContent(themesSchema.getContent());
        if(StringUtils.isEmpty(themesSchema.getId()))
        {
            themesDefinition.setId(idGenerator.nextId());
            themesDefinition.setCreatedById(loggedInUserId);
            themesDefinition.setCreatedOn(Instant.now());
        }
        else
        {
           String id=themesSchema.getId();
           themesDefinition.setId(BigInteger.valueOf(Long.parseLong(id)));
           ThemesDefinition existingThemesDefinition=themesDefinitionRepository.findById(BigInteger.valueOf(Long.parseLong(id))).orElseThrow(()->new ThemesNotFoundByIdException(THEME_NOT_FOUND_EXCEPTION,globalMessageSource.get(THEME_NOT_FOUND_EXCEPTION,id)));
           themesDefinition.setCreatedById(existingThemesDefinition.getCreatedById());
           themesDefinition.setCreatedOn(existingThemesDefinition.getCreatedOn());
        }
        themesDefinition.setUpdatedById(loggedInUserId);
        themesDefinition.setUpdatedOn(Instant.now());
        this.themesDefinitionRepository.save(themesDefinition);
        return this.objectMapper.convertValue(themesDefinition,ThemesResponse.class);
    }

    @Override
    public ThemesResponseSchema getThemesDataById(String id)
    {
        ThemesDefinition theme = this.themesDefinitionRepository.findById(BigInteger.valueOf(Long.parseLong(id)))
                .orElseThrow(()->new ThemesNotFoundByIdException(THEME_NOT_FOUND_EXCEPTION,globalMessageSource.get(THEME_NOT_FOUND_EXCEPTION,id)));
        return this.objectMapper.convertValue(theme,ThemesResponseSchema.class);
    }

    @Override
    public Stream<ThemesResponseSchema> getAllThemesData(String deploymentIdList, String q, Sort sort)
    {
        if(StringUtils.isNotBlank(deploymentIdList))
        {
            String[] idList=deploymentIdList.split(COMMA);
            List<String> deploymentList= Arrays.asList(idList);
            return this.themesDefinitionRepository.findByIdIn(deploymentList).stream()
                    .map(themes ->
                            this.objectMapper.convertValue(themes,ThemesResponseSchema.class));
        }
      if(StringUtils.isNotBlank(q))
      {
          return this.themesDefinitionRepository.findThemesByQSorting(q,sort)
                  .map(themes ->
                          this.objectMapper.convertValue(themes,ThemesResponseSchema.class));
      }
        return this.themesDefinitionRepository.findAll(sort).stream()
                .map(themes ->
                        this.objectMapper.convertValue(themes,ThemesResponseSchema.class));
    }

    @Override
    public PaginationResponsePayload getAllThemesData(String q, Pageable pageable)
    {
        if(StringUtils.isNotBlank(q))
        {
            Page<ThemesDefinition> themesDefinitionPage =this.themesDefinitionRepository.findThemesByQPageable(q,pageable);
            List<Map<String,Object>> themesList=themesDefinitionPage.stream().map(this :: convertEntityToMap).collect(Collectors.toList());
            return tokenUtils.getPaginationResponsePayload(themesDefinitionPage, themesList);
        }
        Page<ThemesDefinition> themesDefinitionPage =this.themesDefinitionRepository.findAll(pageable);
        List<Map<String,Object>> themesList=themesDefinitionPage.stream().map(this::convertEntityToMap).collect(Collectors.toList());
        return tokenUtils.getPaginationResponsePayload(themesDefinitionPage,themesList);
    }

    @Override
    public void deleteThemesDataById(String id)
    {
        if(!themesDefinitionRepository.existsById(BigInteger.valueOf(Long.parseLong(id))))
        {
            throw new ThemesNotFoundByIdException(ENTITY_NOT_FOUND_EXCEPTION,globalMessageSource.get(ENTITY_NOT_FOUND_EXCEPTION, id));
        }
        this.themesDefinitionRepository.deleteById(BigInteger.valueOf(Long.parseLong(id)));
    }

    @Override
    public ResponseEntity<Resource> downloadTheme(String id) throws IOException
    {
            var client = webClientWrapper.createWebClient(tokenUtils.getTokenFromContext());
            JSONObject data = new JSONObject();
            String apiResponse = webClientWrapper.webclientRequest(client, gatewayURL + ACCOUNTS_V1_THEMES_URL +id,GET,null);
            Map<String, Object> response = this.objectMapper.readValue(apiResponse, new TypeReference<>(){});
            Map<String,Object> exportResponse = this.objectMapper.convertValue(response.get(DATA), Map.class);
            data.put(ID,exportResponse.get(ID));
            data.put(THEME_NAME,exportResponse.get(THEME_NAME));
            data.put(THEME_CONTENT,exportResponse.get(THEME_CONTENT));
            data.put(CREATED_ON,exportResponse.get(CREATED_ON));
            data.put(CREATED_BY_ID,exportResponse.get(CREATED_BY_ID));
            data.put(CREATED_BY_NAME,exportResponse.get(CREATED_BY_NAME));
            data.put(UPDATED_BY_ID,exportResponse.get(UPDATED_BY_ID));
            data.put(UPDATED_BY_NAME,exportResponse.get(UPDATED_BY_NAME));
            data.put(UPDATED_ON,exportResponse.get(UPDATED_ON));
            String fileName = String.valueOf(exportResponse.get(THEME_NAME));
            byte[] strToBytes = data.toString().getBytes();
            InputStream inputStream = IOUtils.toInputStream(new String(strToBytes), StandardCharsets.UTF_8);
            InputStreamResource resource = new InputStreamResource(inputStream);
            log.info(DOCUMENT_DOWNLOAD);
            return ResponseEntity.ok()
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(CONTENT_DISPOSITION, ATTACHMENT_FILE_NAME + fileName+ JSON_URL)
                .body(resource);
    }

    @Override
    public ThemesResponse uploadTheme(MultipartFile file, String name) throws IOException
    {
        Map<String,Object> loggedInUserDetails =userDetails.getUserDetails().get(0);
        if (StringUtils.isEmpty(loggedInUserDetails.get(ID).toString()))
        {
            throw new UserDetailsIdNotFoundException(LOGGED_IN_USER_ID_NOT_FOUND,globalMessageSource.get(LOGGED_IN_USER_ID_NOT_FOUND,loggedInUserDetails.get(ID).toString()));
        }
        BigInteger loggedInUserId = BigInteger.valueOf(Long.parseLong(loggedInUserDetails.get(ID).toString()));
        String content = new String(file.getBytes());
        UploadSchema uploadSchema=this.objectMapper.readValue(content,UploadSchema.class);
        ThemesDefinition themesDefinition=this.objectMapper.convertValue(uploadSchema,ThemesDefinition.class);
        String id=uploadSchema.getId();
        if(StringUtils.isEmpty(id))
       {
               id= String.valueOf(idGenerator.nextId());
               themesDefinition.setId(BigInteger.valueOf(Long.parseLong(id)));
               themesDefinition.setName(name);
               themesDefinition.setCreatedById(loggedInUserId);
               themesDefinition.setCreatedOn(Instant.now());
       }
       else
       {
           if(StringUtils.isNotBlank(id))
           {
               String finalId = id;
               ThemesDefinition existingThemesDefinition=themesDefinitionRepository.findById(BigInteger.valueOf(Long.parseLong(id))).orElseThrow(()->new ThemesNotFoundByIdException(THEME_NOT_FOUND_EXCEPTION,globalMessageSource.get(THEME_NOT_FOUND_EXCEPTION,finalId)));
                id=String.valueOf(existingThemesDefinition.getId());
                themesDefinition.setId(existingThemesDefinition.getId());
                themesDefinition.setName(existingThemesDefinition.getName());
           }
       }
       themesDefinition.setUpdatedById(loggedInUserId);
       themesDefinition.setUpdatedOn(Instant.now());
       this.themesDefinitionRepository.save(themesDefinition);
       return new ThemesResponse(id);
    }

    public Map<String,Object> convertEntityToMap(ThemesDefinition themesDefinition)
    {
        ThemesResponseSchema themesResponseSchema=this.objectMapper.convertValue(themesDefinition,ThemesResponseSchema.class);
        return this.objectMapper.convertValue(themesResponseSchema,Map.class);
    }
}
