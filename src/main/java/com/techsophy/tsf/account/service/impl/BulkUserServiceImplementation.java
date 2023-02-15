package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.BulkUserDefinition;
import com.techsophy.tsf.account.exception.*;
import com.techsophy.tsf.account.repository.BulkUploadDefinintionRepository;
import com.techsophy.tsf.account.service.BulkUserService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.AccountConstants.SPACE;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;
import static org.apache.commons.lang3.StringUtils.*;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class BulkUserServiceImplementation implements BulkUserService
{
    @Value(USER_REGISTRATION_FAILURE_EMAIL_TO)
    private final String userRegistrationFailureEmailTo;
    @Value(USER_REGISTRATION_FAILURE_EMAIL_SUBJECT)
    private final String userRegistrationFailureEmailSubject;
    @Value(GATEWAY_URI)
    private final String gatewayUrl;
    private final UserServiceImpl userServiceImpl;
    private final ObjectMapper objectMapper;
    private final GlobalMessageSource globalMessageSource;
    private final IdGeneratorImpl idGenerator;
    private final TokenUtils tokenUtils;
    private final WebClientWrapper webClientWrapper;
    public static final Map<String, String> SUPPORTED_TYPES = Map.of(
            CSV, MediaType.TEXT_PLAIN_VALUE);
    private final BulkUploadDefinintionRepository bulkUploadDefinintionRepository;
    private final UserManagementInKeyCloakImpl userManagementInKeyCloak;

    @Override
    public BulkUploadResponse bulkUploadUsers(MultipartFile file) throws IOException
    {
        Map<String, Object> loggedInUser = userServiceImpl.getCurrentlyLoggedInUserId().get(0);
        List<BulkUserData> bulkUsersList = new ArrayList<>();
        BulkUserDefinition bulkUserDefinition = new BulkUserDefinition();
        log.info(FILE_VALIDATIONS_GOING_ON);
        BigInteger documentId = idGenerator.nextId();
        List<?> dynamicContent = fileProcessing(file);
        dynamicContent.forEach(content->
        {
            LinkedHashMap<String,Object> userDetails = (LinkedHashMap<String, Object>)content;
            Set<String> keys = userDetails.keySet();
            retrieveGroupsFromUserDetails(userDetails, keys);
            retrieveRolesFromUserDetails(userDetails, keys);
            Collection<Object> userValues = userDetails.values();
            List<Object> valuesList = new ArrayList<>(userValues);
            IntStream.range(0,valuesList.size()).forEach(j->
            {
                String errorMsg = USER_INFO_MISSING + (dynamicContent.indexOf(content) + 1) + ROW_AND + (j + 1) + COLUMN_IN_BULK_UPLOAD_USER;
                if (valuesList.get(j).equals(EMPTY))
                {
                    throw new InvalidInputException(errorMsg, errorMsg);
                }
            });
            if (!keys.contains(ID))
            {
                BigInteger bulkId = idGenerator.nextId();
                bulkUserDefinition.setId(bulkId);
                bulkUserDefinition.setUserData(userDetails);
                bulkUserDefinition.setDocumentId(documentId);
                bulkUserDefinition.setStatus(CREATED);
                bulkUserDefinition.setCreatedOn(Instant.now());
                bulkUserDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
            }
            else
            {
                BigInteger bigId = BigInteger.valueOf(Long.parseLong(String.valueOf(userDetails.get(ID))));
                BulkUserDefinition existingBulkDefinition = this.bulkUploadDefinintionRepository.findById(bigId);
                if (existingBulkDefinition == null)
                {
                    throw new BulkUserNotFoundException(CANNOT_UPDATE_RECORD_WITH_GIVEN_ID, globalMessageSource.get(CANNOT_UPDATE_RECORD_WITH_GIVEN_ID,String.valueOf(bigId)));
                }
                bulkUserDefinition.setId(existingBulkDefinition.getId());
                bulkUserDefinition.setDocumentId(existingBulkDefinition.getDocumentId());
                bulkUserDefinition.setUserData(userDetails);
                bulkUserDefinition.setStatus(UPDATED);
                bulkUserDefinition.setCreatedOn(existingBulkDefinition.getCreatedOn());
                bulkUserDefinition.setCreatedById(existingBulkDefinition.getCreatedById());
            }
            bulkUserDefinition.setUpdatedOn(Instant.now());
            bulkUserDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
            BulkUserData bulkUserData = this.objectMapper.convertValue(bulkUserDefinition, BulkUserData.class);
            bulkUsersList.add(bulkUserData);
        });
        bulkUsersList.forEach(bulkUserDefinitionData ->
        {
            BulkUserDefinition bulkUserDefinition1 = this.objectMapper.convertValue(bulkUserDefinitionData, BulkUserDefinition.class);
            this.bulkUploadDefinintionRepository.save(bulkUserDefinition1);
        });
        log.info(CALLING_BPMN);
        Map<String, String> usersMap = new HashMap<>();
        String deploymentIdConvertedToString = String.valueOf(bulkUserDefinition.getDocumentId());
        usersMap.put(DOCUMENT_ID, deploymentIdConvertedToString);
        usersMap.put(USER_REG_FAILURE_EMAIL_TO, userRegistrationFailureEmailTo);
        usersMap.put(USER_REG_FAILURE_EMAIL_SUBJECT, userRegistrationFailureEmailSubject);
        Map<String, Object> payload = new HashMap<>();
        payload.put(PROCESS_DEFINITION_KEY, PROCESS_ID);
        payload.put(BUSINESS_KEY, file.getOriginalFilename());
        payload.put(VARIABLES, usersMap);
        WebClient webClient = webClientWrapper.createWebClient(tokenUtils.getTokenFromContext());
        try
        {
            String response=webClientWrapper.webclientRequest(webClient, gatewayUrl + WORKFLOW_START_URL, POST, payload);
            log.info(response);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return new BulkUploadResponse(String.valueOf(bulkUserDefinition.getDocumentId()));
    }

    private void retrieveRolesFromUserDetails(LinkedHashMap<String, Object> userDetails, Set<String> keys) {
        if (!keys.contains(ROLES))
        {
            userDetails.put(ROLES, List.of());
        }
        else
        {
            List<RolesSchema> keyCloakRolesSchema = null;
            try
            {
                keyCloakRolesSchema=userManagementInKeyCloak.getAllRoles();
            }
            catch (JsonProcessingException e)
            {
                e.printStackTrace();
            }
            assert keyCloakRolesSchema != null;
            Set<String> keyCloakRoleNames = keyCloakRolesSchema.stream().map(RolesSchema::getRoleName).collect(Collectors.toSet());
            String roleValues = this.objectMapper.convertValue(userDetails.get(ROLES), String.class);
            Set<String> rolesList = Arrays.stream(roleValues.split(COMMA)).collect(Collectors.toSet());
            if(!Objects.equals(roleValues, EMPTY))
            {
                if (keyCloakRoleNames.containsAll(rolesList))
                {
                    userDetails.replace(ROLES, rolesList);
                }
                else
                {
                    throw new NoDataFoundException(GIVEN_ROLES_NOT_FOUND, globalMessageSource.get(GIVEN_ROLES_NOT_FOUND));
                }
            }
        }
    }

    private void retrieveGroupsFromUserDetails(LinkedHashMap<String, Object> userDetails, Set<String> keys) {
        if (!keys.contains(GROUPS)) //group 
        {
            
            userDetails.put(GROUPS, List.of());
        }
        else
        {
            Set<GroupsSaveSchema> keycloakGroupsSchema = null;
            try
            {
                keycloakGroupsSchema = userManagementInKeyCloak.getAllGroups().collect(Collectors.toSet());
            }
            catch (JsonProcessingException e)
            {
                e.printStackTrace();
            }
            assert keycloakGroupsSchema != null;
            Set<String> keycloakGroupNames = keycloakGroupsSchema.stream().map(GroupsSaveSchema::getName).collect(Collectors.toSet());
            String groupValues = this.objectMapper.convertValue(userDetails.get(GROUPS), String.class);
            if(!Objects.equals(groupValues, EMPTY))
            {
                Set<String> groupList = Arrays.stream(groupValues.split(COMMA)).collect(Collectors.toSet());
                if (keycloakGroupNames.containsAll(groupList))
                {
                    userDetails.replace(GROUPS, groupList);
                }
                else
                {
                    throw new NoDataFoundException(GIVEN_GROUPS_NOT_FOUND, globalMessageSource.get(GIVEN_GROUPS_NOT_FOUND));
                }
            }
        }
    }

    @Override
    public BulkUploadResponse bulkUpdateStatus(BulkUploadSchema bulkUploadSchema) throws JsonProcessingException
    {
        Map<String, Object> loggedInUser = userServiceImpl.getCurrentlyLoggedInUserId().get(0);
        BulkUserDefinition bulkUserDefinition = new BulkUserDefinition();
        BigInteger id = BigInteger.valueOf(Long.parseLong(bulkUploadSchema.getId()));
        if (!bulkUploadDefinintionRepository.existsById(id))
        {
            throw new BulkUserNotFoundException(USER_NOT_FOUND_BY_ID, globalMessageSource.get(USER_NOT_FOUND_BY_ID, id));
        }
        BulkUserDefinition existingBulkDefinition = this.bulkUploadDefinintionRepository.findById(id);
        bulkUserDefinition.setUserData(existingBulkDefinition.getUserData());
        bulkUserDefinition.setStatus(bulkUploadSchema.getStatus());
        bulkUserDefinition.setId(existingBulkDefinition.getId());
        bulkUserDefinition.setDocumentId(existingBulkDefinition.getDocumentId());
        bulkUserDefinition.setCreatedOn(existingBulkDefinition.getCreatedOn());
        bulkUserDefinition.setCreatedById(existingBulkDefinition.getCreatedById());
        bulkUserDefinition.setUpdatedOn(Instant.now());
        bulkUserDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        this.bulkUploadDefinintionRepository.save(bulkUserDefinition);
        return new BulkUploadResponse(String.valueOf(existingBulkDefinition.getDocumentId()));
    }

    @Override
    public List<BulkUserResponse> getAllBulkUsers(String filterColumn, String filterValue, String sortBy, String sortOrder)
    {
        if (StringUtils.isEmpty(sortBy)&&StringUtils.isEmpty(sortOrder))
        {
            return this.bulkUploadDefinintionRepository.findBulkUsersByFilter(filterColumn,filterValue).stream().map(this::convertBulkEntityToDTO).collect(Collectors.toList());
        }
        return this.bulkUploadDefinintionRepository.findBulkUsersByFilterAndSort(filterColumn, filterValue, sortBy,sortOrder).stream().map(this::convertBulkEntityToDTO).collect(Collectors.toList());
    }

    @Override
    public PaginationResponsePayload getAllBulkUsers(String filterColumn, String filterValue, String sortBy, String sortOrder, Pageable pageable)
    {
        if (StringUtils.isEmpty(sortBy)&&StringUtils.isEmpty(sortOrder))
        {
            Page<BulkUserDefinition> bulkUserDefinitionPage = this.bulkUploadDefinintionRepository.findBulkUserByFilterPageable(filterColumn, filterValue,pageable);
            List<Map<String, Object>> bulkUserSchemaList = bulkUserDefinitionPage.stream().map(this::convertBulkEntityToMap).collect(Collectors.toList());
            return tokenUtils.getPaginationResponsePayload(bulkUserDefinitionPage, bulkUserSchemaList);
        }
        Page<BulkUserDefinition> bulkUserDefinitionPage = this.bulkUploadDefinintionRepository.findBulkUserByFilterSortPageable(filterColumn,filterValue,sortBy,sortOrder,pageable);
        List<Map<String, Object>> bulkUserSchemaList = bulkUserDefinitionPage.stream().map(this::convertBulkEntityToMap).collect(Collectors.toList());
        return tokenUtils.getPaginationResponsePayload(bulkUserDefinitionPage, bulkUserSchemaList);
    }

    @Override
    public List<BulkUserResponse> getAllBulkUsers(String q, String sortBy, String sortOrder)
    {
        if (isEmpty(sortBy)&&isEmpty(sortOrder))
        {
            return this.bulkUploadDefinintionRepository.findBulkUsersByQ(q).stream().map(this::convertBulkEntityToDTO).collect(Collectors.toList());
        }
        return this.bulkUploadDefinintionRepository.findBulkUsersByQSort(q,sortBy,sortOrder).stream().map(this::convertBulkEntityToDTO).collect(Collectors.toList());
    }

    @Override
    public PaginationResponsePayload getAllBulkUsers(String q, String sortBy, String sortOrder, Pageable pageable)
    {
        if (isEmpty(sortBy)&&isEmpty(sortOrder))
        {
            Page<BulkUserDefinition> bulkUserDefinitionPage = this.bulkUploadDefinintionRepository.findBulkUsersByQPageable(q,pageable);
            List<Map<String, Object>> bulkUserSchemaList = bulkUserDefinitionPage.stream().map(this::convertBulkEntityToMap).collect(Collectors.toList());
            return tokenUtils.getPaginationResponsePayload(bulkUserDefinitionPage, bulkUserSchemaList);
        }
        Page<BulkUserDefinition> bulkUserDefinitionPage = this.bulkUploadDefinintionRepository.findBulkUsersByQSortPageable(q,sortBy,sortOrder,pageable);
        List<Map<String, Object>> bulkUserSchemaList = bulkUserDefinitionPage.stream().map(this::convertBulkEntityToMap).collect(Collectors.toList());
        return tokenUtils.getPaginationResponsePayload(bulkUserDefinitionPage, bulkUserSchemaList);
    }

    @Override
    public void deleteBulkUserById(String id)
    {
        if (!bulkUploadDefinintionRepository.existsById(BigInteger.valueOf(Long.parseLong(id))))
        {
            throw new BulkUserNotFoundException(USER_NOT_FOUND_BY_ID, globalMessageSource.get(USER_NOT_FOUND_BY_ID, id));
        }
        this.bulkUploadDefinintionRepository.deleteById(BigInteger.valueOf(Long.parseLong(id)));
    }

    public BulkUserResponse convertBulkEntityToDTO(BulkUserDefinition bulkUserDefinition)
    {
        return this.objectMapper.convertValue(bulkUserDefinition, BulkUserResponse.class);
    }

    public Map<String, Object> convertBulkEntityToMap(BulkUserDefinition bulkUserDefinition)
    {
        return this.objectMapper.convertValue(bulkUserDefinition, Map.class);
    }

    public void checkValidFileName(String fileName)
    {
        if (!fileName.contains(DOT))
        {
            throw new InvalidDataException(INVALID_FILE_NAME_OR_EXTENSION, globalMessageSource.get(INVALID_FILE_NAME_OR_EXTENSION));
        }
        if (fileName.split(REGEX_SPLIT).length > 2)
        {
            throw new InvalidDataException(INVALID_FILE_NAME_OR_EXTENSION, globalMessageSource.get(INVALID_FILE_NAME_OR_EXTENSION));
        }
    }

    private List<?> fileProcessing(MultipartFile file) throws IOException
    {
        List<?> dynamicContent;
        if (file.isEmpty())
        {
            throw new NoDataFoundException(PLEASE_SELECT_CSV_FILE_WITH_DATA, globalMessageSource.get(PLEASE_SELECT_CSV_FILE_WITH_DATA));
        }
        log.info(UPLOADING_CSV_FILE,file.getOriginalFilename());
        String fileName = file.getOriginalFilename();
        if (StringUtils.isBlank(fileName) || StringUtils.isEmpty(fileName))
        {
            throw new FileNameNotPresentException(CSV_FILE_NAME_NOT_EMPTY, globalMessageSource.get(CSV_FILE_NAME_NOT_EMPTY));
        }
        checkValidFileName(fileName);
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (SUPPORTED_TYPES.containsKey(fileExtension))
        {
            CsvSchema csvSchema = CsvSchema.builder().setUseHeader(true).build();
            CsvMapper csvMapper = new CsvMapper();
            dynamicContent = csvMapper.readerFor(Map.class).with(csvSchema).readValues(file.getBytes()).readAll();
        }
        else
        {
            throw new InvalidInputException(UPLOAD_CSV_FILE_EXTENSION_ONLY, globalMessageSource.get(UPLOAD_CSV_FILE_EXTENSION_ONLY));
        }
        return dynamicContent;
    }
}