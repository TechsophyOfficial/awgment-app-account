package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.GroupDefinition;
import com.techsophy.tsf.account.exception.*;
import com.techsophy.tsf.account.repository.GroupRepository;
import com.techsophy.tsf.account.service.GroupsDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.validation.ConstraintViolationException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@RefreshScope
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class GroupsDataServiceImpl implements GroupsDataService
{
    private final UserManagementInKeyCloakImpl userManagementInKeyCloak;
    private final GlobalMessageSource globalMessageSource;
    private final ObjectMapper objectMapper;
    private final GroupRepository groupRepository;
    private final TokenUtils tokenUtils;
    private final UserServiceImpl userServiceImpl;
    private final WebClientWrapper webClientWrapper;
    private final IdGeneratorImpl idGenerator;
    @Value(USER_MANAGEMENT_KEYCLOAK_API)
    private final String keyCloakApi;
    @Value(USER_MANAGEMENT_GROUPS_API)
    private final String getGroupsApi;

    @Override
    public List<GroupsDataSchema> getAllGroups(String q, Sort sort,String deploymentIdList) throws JsonProcessingException
    {
        if(StringUtils.isNotBlank(deploymentIdList))
        {
            String[] idList=deploymentIdList.split(COMMA);
            List<String> deploymentList= Arrays.asList(idList);
            List<GroupDefinition> groupDefinition = groupRepository.findByIdIn(deploymentList);
            return this.objectMapper.convertValue(groupDefinition, new TypeReference<>()
            {
            });
        }
        if(StringUtils.isEmpty(q))
        {
            List<GroupDefinition> groupDefinition = groupRepository.findAll(sort);
            return this.objectMapper.convertValue(groupDefinition, new TypeReference<>() {});
        }
        List<GroupDefinition> groupDefinition = groupRepository.findGroupsByQSorting(q,sort);
        return this.objectMapper.convertValue(groupDefinition, new TypeReference<>()
        {
        });
    }

    @Override
    public PaginationResponsePayload getAllGroups(String q, Pageable pageable) throws JsonProcessingException
    {
        if(StringUtils.isEmpty(q))
        {
            Page<GroupDefinition> groupsDefinitionPageable = groupRepository.findAll(pageable);
            return groupsPagination(groupsDefinitionPageable);
        }
        Page<GroupDefinition> groupsDefinitionPageable = groupRepository.findGroupsByQPageable(q,pageable);
        return  groupsPagination(groupsDefinitionPageable);
    }

    @Override
    public GroupsDataSchema getGroupById(String id) throws JsonProcessingException
    {
        var client = webClientWrapper.createWebClient(tokenUtils.getTokenFromContext());
        GroupDefinition groupsDefinition = groupRepository.findById(new BigInteger(id))
                .orElseThrow(() -> new InvalidInputException(UNABLE_TO_GET_GROUPS_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUPS_WITH_ID,id)));
        GroupsDataSchema groupsDataSchema = this.objectMapper.convertValue(groupsDefinition, GroupsDataSchema.class);
        if(StringUtils.isNotEmpty(groupsDefinition.getGroupId()))
        {
            String groupId=groupsDefinition.getGroupId();
            String response = null;
            try
            {
                 response = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ getGroupsApi + URL_SEPERATOR + groupId ,GET,null);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            if(StringUtils.isEmpty(response) || response.contains(ERROR))
            {
                throw new GroupsNotFoundException(UNABLE_TO_GET_GROUP_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUP_WITH_ID));
            }
            Map<String, Object> group = this.objectMapper.readValue(response, new TypeReference<>() {});
            List<String> realmRoles = (List<String>) group.get(REALM_ROLES);
            LinkedHashMap<String,Object> clientRolesMap= (LinkedHashMap<String, Object>) group.get(CLIENT_ROLES);
            List<String> clientRolesList=new ArrayList<>();
            for(Map.Entry<String,Object>  clientRolesEntry : clientRolesMap.entrySet())
            {
               List<String> clientRoles = this.objectMapper.convertValue(clientRolesEntry.getValue(),List.class);
               clientRolesList.addAll(clientRoles);
            }
            return getKeycloakRoles(client, groupsDataSchema, realmRoles, clientRolesList);
        }
        else
        {
            throw new GroupsNotFoundException(UNABLE_TO_GET_GROUP_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUP_WITH_ID));
        }
    }

    private GroupsDataSchema getKeycloakRoles(WebClient client, GroupsDataSchema groupsDataSchema, List<String> realmRoles, List<String> clientRolesList) throws JsonProcessingException {
        String keycloakRoles=null;
        try
        {
            keycloakRoles = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ ROLES_URL,GET,null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        if (StringUtils.isEmpty(keycloakRoles) )
        {
            throw new GroupsNotFoundException(UNABLE_TO_GET_ROLES,globalMessageSource.get(UNABLE_TO_GET_ROLES));
        }
        List<Map<String, Object>> keycloakRolesMapping = this.objectMapper.readValue(keycloakRoles, new TypeReference<>() {});
        List<String> assignedGroupRoles = keycloakRolesMapping.stream().map(keycloakRole ->
        {
            if (realmRoles.contains(String.valueOf(keycloakRole.get(ROLE_NAME))))
            {
                return keycloakRole.get(NAME).toString();
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        assignedGroupRoles.addAll(clientRolesList);
        return groupsDataSchema.withRoles(assignedGroupRoles);
    }

    @Override
    public GroupsDataSchema saveGroup(GroupsData groupsData)
    {
        try {
            Map<String,Object> loggedInUser = userServiceImpl.getCurrentlyLoggedInUserId().get(0);
            if (StringUtils.isEmpty(groupsData.getId()))
            {
                if (groupRepository.existsByName(groupsData.getName()))
                {
                    throw new InvalidInputException(UNABLE_TO_ADD_GROUP,globalMessageSource.get(UNABLE_TO_ADD_GROUP,groupsData.getName()));
                }
                GroupsDataSchema groupsDataSchema = this.objectMapper.convertValue(groupsData, GroupsDataSchema.class).withId(String.valueOf(idGenerator.nextId()));
                GroupDefinition groupDefinition = this.objectMapper.convertValue(groupsDataSchema, GroupDefinition.class);
                groupDefinition.setCreatedOn(Instant.now());
                BigInteger userId = new BigInteger(loggedInUser.get(ID).toString());
                groupDefinition.setCreatedById(userId);
                groupDefinition.setUpdatedOn(Instant.now());
                groupDefinition.setUpdatedById(userId);
                groupRepository.save(groupDefinition);
                return this.objectMapper.convertValue(groupDefinition, GroupsDataSchema.class)
                        .withCreatedOn(groupDefinition.getCreatedOn());
            }
            GroupsDataSchema groupsDataSchema = this.objectMapper.convertValue(groupsData, GroupsDataSchema.class);
            List<GroupDefinition> groupsDefinition = groupRepository.findAll();
            List<GroupDefinition> update = groupsDefinition.stream()
                    .filter(data ->
                            !String.valueOf(data.getId()).equals(groupsData.getId()) && data.getName().equals(groupsData.getName()))
                    .collect(Collectors.toList());
            if (!update.isEmpty())
            {
                throw new InvalidInputException(UNABLE_TO_ADD_GROUP,globalMessageSource.get(UNABLE_TO_ADD_GROUP));
            }
            if (groupRepository.existsById(new BigInteger(groupsData.getId())))
            {
                GroupDefinition existingDetails = groupRepository.findById(new BigInteger(groupsData.getId())).orElseThrow(() ->new GroupsNotFoundException(UNABLE_TO_GET_GROUPS,globalMessageSource.get(UNABLE_TO_GET_GROUPS)));
                GroupDefinition groupDefinition = this.objectMapper.convertValue(groupsDataSchema, GroupDefinition.class);
                groupDefinition.setCreatedOn(existingDetails.getCreatedOn());
                groupDefinition.setCreatedById(existingDetails.getCreatedById());
                groupDefinition.setUpdatedOn(Instant.now());
                groupDefinition.setUpdatedById(new BigInteger(loggedInUser.get(ID).toString()));
                groupRepository.save(groupDefinition);
                return this.objectMapper.convertValue(groupDefinition, GroupsDataSchema.class)
                        .withUpdatedOn(groupDefinition.getUpdatedOn());
            }
            else
            {
                throw new InvalidInputException(UNABLE_TO_GET_GROUPS_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUPS_WITH_ID, groupsData.getId()));        }}
        catch (ConstraintViolationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RunTimeException(e.getMessage());
        }
    }

    @Override
    public void deleteGroup(String id)
    {
        BigInteger groupId = new BigInteger(id);
        if (groupRepository.existsById(groupId))
        {
            groupRepository.deleteById(groupId);
        }
        else
        {
            throw new InvalidInputException(UNABLE_TO_DELETE_GROUP_WITH_THIS_ID,globalMessageSource.get(UNABLE_TO_DELETE_GROUP_WITH_THIS_ID, id));
        }

    }

    @Override
    public void assignRolesToGroup(String id,AssignGroupRoles groupRoles) throws JsonProcessingException
    {
        String token= tokenUtils.getTokenFromContext();
        BigInteger bigId = new BigInteger(id);
        var client = webClientWrapper.createWebClient(tokenUtils.getTokenFromContext());
        if (groupRepository.existsById(bigId))
        {
            String groupId= groupRepository.findById(bigId).orElseThrow(RuntimeException::new).getGroupId();
            String response;
            try
            {
              response = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ getGroupsApi + URL_SEPERATOR + groupId + ROLE_MAPPINGS_REALM_URL,GET,null);
            }
           catch (Exception e)
           {
               throw new RealmAcessException(REALM_ROLES_NOT_ACCESSIBLE,globalMessageSource.get(REALM_ROLES_NOT_ACCESSIBLE));
           }
            if (StringUtils.isEmpty(response))
            {
                throw new GroupsNotFoundException(UNABLE_TO_GET_GROUPS_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUPS_WITH_ID));
            }
            List<Map<String, Object>> groups = this.objectMapper.readValue(response, new TypeReference<>(){});
            webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+getGroupsApi + URL_SEPERATOR + groupId + ROLE_MAPPINGS_REALM_URL,DELETE, groups);
            List<String> givenRoles = groupRoles.getRoles();
            Map<String,String> clientMap=userManagementInKeyCloak.getClientMap(token);
            Map<String,List<RolesSchema>> rolesMap=userManagementInKeyCloak.getAllClientAndDefaultRoles();
            for(Map.Entry<String,List<RolesSchema>> rolesEntry : rolesMap.entrySet())
            {
                String clientName = rolesEntry.getKey();
                List<RolesSchema> roles = rolesEntry.getValue();
                List<Map<String,String>> addRoleList = roles.stream().map(role ->
                {
                    if (givenRoles.contains(role.getRoleName()))
                    {
                        Map<String,String> addRole = new HashMap<>();
                        addRole.put(ID, role.getRoleId());
                        addRole.put(ROLE_NAME,role.getRoleName());
                        return addRole;
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
                if(clientName.equalsIgnoreCase(DEFAULT_ROLES))
                {
                   webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtils.getIssuerFromContext()+getGroupsApi+URL_SEPERATOR+groupId+ROLE_MAPPINGS_REALM_URL,POST,addRoleList);
                }
                else if(!addRoleList.isEmpty())
                {
                    webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtils.getIssuerFromContext()+getGroupsApi+URL_SEPERATOR+groupId+ROLE_MAPPINGS_CLIENT_URL+clientMap.get(clientName),POST,addRoleList);
                }
            }
        }
    }

    public PaginationResponsePayload groupsPagination( Page<GroupDefinition> groupsDefinitionPageable)
    {
        Stream<Map<String,Object>> groupsDataSchema = groupsDefinitionPageable.stream()
                .map(groupDefinitionData ->
                {
                    GroupsDataSchema groupsSchema= this.objectMapper.convertValue(groupDefinitionData,GroupsDataSchema.class);
                    return this.objectMapper.convertValue(groupsSchema,Map.class);
                });
        return tokenUtils.getPaginationResponsePayload(groupsDefinitionPageable, groupsDataSchema.collect(Collectors.toList()));
    }
}
