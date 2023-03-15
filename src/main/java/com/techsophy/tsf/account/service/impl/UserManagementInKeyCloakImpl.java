package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.AccountConstants;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.exception.*;
import com.techsophy.tsf.account.service.UserManagementInKeyCloak;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@RefreshScope
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
@Slf4j
public class UserManagementInKeyCloakImpl implements UserManagementInKeyCloak
{
    private final GlobalMessageSource globalMessageSource;
    private final ObjectMapper objectMapper;
    private final WebClientWrapper webClientWrapper;
    private final TokenUtils tokenUtils;
    private  static Random randomMethod = new Random();
    @Value(AccountConstants.USER_MANAGEMENT_KEYCLOAK_API)
    private final String keyCloakApi;
    @Value(USER_MANAGEMENT_USER_API)
    String userCreationApi;
    @Value(USER_MANAGEMENT_ROLES_API)
    String getRolesApi;
    @Value(USER_MANAGEMENT_GROUPS_API)
    private final String getGroupsApi;
    @Value(USER_MANAGEMENT_COUNT_API)
    private final String getCountApi;
    @Value(DEFAULT_PAGE_LIMIT)
    private final String defaultPageLimit;
    @Value(EMAIL_VALIDITY)
    private final String emailValidity;
    @Value(CLIENTS)
    private final String requiredClientsCSV;
    private final UserServiceImpl userServiceImpl;

    @Override
    public Map<String, Object> createUser(UserDataSchema userData) throws JsonProcessingException
    {
        String token= tokenUtils.getTokenFromContext();
        var userSchema = objectMapper
                .convertValue(userData.getUserData(), UserData.class);
        Map<String,Object> attributeMap = new HashMap<>();
        attributeMap.put("userId",userSchema.getId());
        Map<String, String> realmDetails;
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        Map<String, Object> userModel = new HashMap<>();
        Map<String, Object> passwordCred = new HashMap<>();
        userModel.put(USER_SCHEMA_FIRST_NAME, userSchema.getFirstName());
        userModel.put(USER_SCHEMA_LAST_NAME, userSchema.getLastName());
        userModel.put(USER_SCHEMA_EMAIL, userSchema.getEmailId());
        userModel.put(USER_SCHEMA_USER_NAME, userSchema.getUserName());
        userModel.put("attributes", attributeMap);

        var client = webClientWrapper.createWebClient(token);
        if (StringUtils.isEmpty(userData.getUserId()))
        {
            String password = generatePassword();
            userModel.put(USER_SCHEMA_ENABLED, true);
            userModel.put(USER_SCHEMA_USER_NAME, userData.getUserData().get(USER_SCHEMA_USER_NAME));
            passwordCred.put(USER_SCHEMA_TYPE,USER_SCHEMA_PASSWORD);
            passwordCred.put(USER_SCHEMA_VALUE, password);
            passwordCred.put(USER_SCHEMA_TEMPORARY, false);
            userModel.put(USER_SCHEMA_CREDENTIALS, List.of(passwordCred));
            userModel.put(USER_SCHEMA_REQUIRED_ACTIONS, List.of(USER_SCHEMA_UPDATE_PASSWORD));
            userModel.put(USER_NAME, userData.getUserData().get(USER_NAME_DATA));
            log.info(USERCREDENTIALS+userModel.get(USERNAME)+PASSWORD+passwordCred.get(VALUE)+EMAIL_NAME+userModel.get(EMAIL));
            String response = webClientWrapper.webclientRequest(client,keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi,POST,userModel);
            if (StringUtils.isNotEmpty(response))
            {
                realmDetails = this.objectMapper
                        .readValue(response, new TypeReference<>()
                        {
                        });
                String error = realmDetails.values().stream().findFirst().orElseThrow(RuntimeException::new);
                throw new InvalidInputException(UNABLE_TO_ADD_USER,globalMessageSource.get(UNABLE_TO_ADD_USER,error));
            }
            Map<String, Object> userDetails = getUserIdByUsername(token, userSchema.getUserName());
            if (userDetails == null)
            {
                throw new UserNotFoundException(UNABLE_TO_FIND_USER,globalMessageSource.get(UNABLE_TO_FIND_USER));
            }
            userDetails.put(USER_SCHEMA_PASSWORD,password);
            return userDetails;
        } else
        {
            Map<String, Object> userDetails = getUserIdByUsername(token, userData.getUserData().containsKey(OLD_USER_NAME)?String.valueOf(userData.getUserData().get("oldUserName")):userSchema.getUserName());
            if (userDetails == null)
            {
                throw new UserNotFoundException(UNABLE_TO_FIND_USER,globalMessageSource.get(UNABLE_TO_FIND_USER));
            }
            String response = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi +URL_SEPERATOR+ userDetails.get(ID),PUT,userModel);
            if (StringUtils.isNotEmpty(response))
            {
                realmDetails = this.objectMapper
                        .readValue(response, new TypeReference<>()
                        {
                        });
                String error = realmDetails.values()
.stream().findFirst().orElseThrow(RuntimeException::new);
                throw new InvalidInputException(UNABLE_TO_UPDATE_USER,globalMessageSource.get(UNABLE_TO_UPDATE_USER,error));
            }
            userDetails.put("userName",userSchema.getUserName());
            return userDetails;
        }
    }

    public Map<String,String> getClientMap(String token) throws JsonProcessingException
    {
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        String response;
        try
        {
            response = webClientWrapper.webclientRequest(client,keyCloakApi+tokenUtils.getIssuerFromToken(tokenUtils.getTokenFromContext())+GET_ALL_CLIENTS_URL,GET,null);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw new ClientsUnableToRetrieveException(UNABLE_TO_FETCH_CLIENTS,globalMessageSource.get(UNABLE_TO_FETCH_CLIENTS));
        }
        List<Map<String, Object>> availableClientsInfo = this.objectMapper.readValue(response, new TypeReference<>() {});
        List<String> requiredClientsList=Arrays.asList(requiredClientsCSV.split(COMMA));
        Map<String,String> clientMap =new HashMap<>();
        for(Map<String,Object> c: availableClientsInfo)
        {
            if(requiredClientsList.contains(String.valueOf(c.get(CLIENT_ID))))
            {
                clientMap.put(String.valueOf(c.get(CLIENT_ID)),String.valueOf(c.get(ID)));
            }
        }

        return clientMap;
    }

    private void assignRole(String token,UserRolesSchema userRoles) throws JsonProcessingException
    {
        Map<String, String> realmDetails;
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        Map<String,String> clientMap=getClientMap(token);
        Map<String,List<RolesSchema>> rolesMap=getAllClientAndDefaultRoles();
        for(Map.Entry<String ,List<RolesSchema>> rolesEntry : rolesMap.entrySet())
        {
            List<RolesSchema> roles = rolesEntry.getValue();
            String clientName = rolesEntry.getKey();
            List<Map<String,String>> addRoleList = roles.stream().map(role ->
            {
                if (userRoles.getRoles().contains(role.getRoleName()))
                {
                    Map<String,String> addRole = new HashMap<>();
                    addRole.put(ID, role.getRoleId());
                    addRole.put(ROLE_NAME,role.getRoleName());
                    return addRole;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            String roleResponse = null;
            if(clientName.equalsIgnoreCase(DEFAULT_ROLES))
            {
                roleResponse = webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtils.getIssuerFromContext()+userCreationApi+URL_SEPERATOR+userRoles.getUserId()+ROLE_MAPPINGS_REALM_URL,POST,addRoleList);
            }
            else if(!addRoleList.isEmpty())
            {
                roleResponse = webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtils.getIssuerFromContext()+userCreationApi+URL_SEPERATOR+userRoles.getUserId()+ROLE_MAPPINGS_CLIENT_URL+clientMap.get(clientName),POST,addRoleList);
            }
            if (StringUtils.isNotEmpty(roleResponse))
            {
                realmDetails = this.objectMapper.readValue(roleResponse, new TypeReference<>(){});
                String error = realmDetails.values().stream().findFirst().orElseThrow(RuntimeException::new);
                throw new InvalidInputException(UNABLE_TO_ADD_ROLE,globalMessageSource.get(UNABLE_TO_ADD_ROLE,error));
            }

        }
    }

    @Override
    public void assignUserRole(UserRolesSchema userRoles) throws JsonProcessingException
    {
        String token= tokenUtils.getTokenFromContext();
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        List<RolesSchema> deleteRoles = getUserRoles(token,userRoles.getUserId());
        if (!deleteRoles.isEmpty())
        {
            deleteUserRoles(token, userRoles.getUserId(),deleteRoles);
        }
        assignRole(token,userRoles);
    }

    private List<RolesSchema> getUserRoles(String token,String userId) throws JsonProcessingException
    {
        List<String> defaultRoles = new ArrayList<>();
        var client = webClientWrapper.createWebClient(token);
        Map<String, Object> realmDetails = getRealmDetails(token);
        if (realmDetails.containsKey(DEFAULT_ROLES))
        {
            defaultRoles = (List<String>) realmDetails.get(DEFAULT_ROLES);
        }
        String response = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi +URL_SEPERATOR+ userId + ROLE_MAPPINGS_REALM_URL,GET,null);
        var roleObject = new JSONTokener(response).nextValue();
        if (roleObject instanceof JSONObject)
        {
            throw new InvalidInputException(UNABLE_TO_GET_USER_ROLES,globalMessageSource.get(UNABLE_TO_GET_USER_ROLES,roleObject));
        }
        List<String> finalDefaultRoles = defaultRoles;
        return ((JSONArray)roleObject).toList().stream().map(roleDetails ->
        {
            HashMap<String,String> roles = this.objectMapper.convertValue(roleDetails,HashMap.class);
            var rolesSchema = this.objectMapper.convertValue(roles,RolesSchema.class)
                    .withRoleId(roles.get(ID))
                    .withRoleName(roles.get(ROLE_NAME));
            if (finalDefaultRoles.isEmpty()||!finalDefaultRoles.contains(rolesSchema.getRoleName()))
                return rolesSchema;
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void deleteUserRoles(String token,String userId,List<RolesSchema> deleteRoles)
    {
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        List<Map<String, Object>> addRoleList = deleteRoles.stream().map(role ->
        {
            Map<String, Object> addRole = new HashMap<>();
            addRole.put(ID, role.getRoleId());
            addRole.put(ROLE_NAME, role.getRoleName());
            return addRole;
        }).collect(Collectors.toList());
        String roleResponse = webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext() + userCreationApi +URL_SEPERATOR+ userId + ROLE_MAPPINGS_REALM_URL,DELETE,addRoleList);
        if (StringUtils.isNotEmpty(roleResponse))
            throw new InvalidInputException(UNABLE_TO_DELETE_ROLES,globalMessageSource.get(UNABLE_TO_DELETE_ROLES));
    }

    public Map<String,List<RolesSchema>> getAllClientAndDefaultRoles() throws JsonProcessingException
    {
        List<RolesSchema> rolesSchemas=new ArrayList<>();
        String token = tokenUtils.getTokenFromContext();
        var client = webClientWrapper.createWebClient(token);
        String response;
        try
        {
            response = webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext()+getRolesApi,GET,null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw new RolesNotFoundException(UNABLE_TO_GET_ROLES,globalMessageSource.get(UNABLE_TO_GET_ROLES));
        }
        List<Map<String, Object>> rolesStream = this.objectMapper.readValue(response,new TypeReference<>() {});
        for(Map<String,Object> role:rolesStream)
        {
            RolesSchema rolesSchema=new RolesSchema(String.valueOf(role.get(ID)),String.valueOf(role.get(ROLE_NAME)));
            rolesSchemas.add(rolesSchema);
        }
        Map<String,List<RolesSchema>> totalRoles=new HashMap<>();
        totalRoles.put(DEFAULT_ROLES,rolesSchemas);
        try
        {
            response = webClientWrapper.webclientRequest(client,keyCloakApi+tokenUtils.getIssuerFromToken(tokenUtils.getTokenFromContext())+GET_ALL_CLIENTS_URL,GET,null);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw new ClientsUnableToRetrieveException(UNABLE_TO_FETCH_CLIENTS,globalMessageSource.get(UNABLE_TO_FETCH_CLIENTS));
        }
        Map<String,String> clientMap=getClientMap(token);
        for(Map.Entry<String,String> clientName: clientMap.entrySet())
        {
            try
            {
                response = webClientWrapper.webclientRequest(client,keyCloakApi+tokenUtils.getIssuerFromToken(tokenUtils.getTokenFromContext())+GET_CLIENT_ROLES_URL+clientName.getValue()+ROLES_URL,GET,null);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            List<Map<String,Object>> rolesMap= this.objectMapper.readValue(response, new TypeReference<>() {});
            List<RolesSchema> rolesSchemas1=new ArrayList<>();
            for(Map<String,Object> role:rolesMap)
            {
                RolesSchema rolesSchema=new RolesSchema(String.valueOf(role.get(ID)),String.valueOf(role.get(NAME)));
                rolesSchemas1.add(rolesSchema);
            }
            totalRoles.put(clientName.getKey(),rolesSchemas1);
        }
        return totalRoles;
    }

    @Override
    public List<RolesSchema> getAllRoles() throws JsonProcessingException
    {
        List<RolesSchema> rolesSchemas = new ArrayList<>();
        Map<String,List<RolesSchema>> allRoles=getAllClientAndDefaultRoles();
        for(Map.Entry<String,List<RolesSchema>> rolesEntry:allRoles.entrySet())
        {
            rolesSchemas.addAll(rolesEntry.getValue());
        }
        return rolesSchemas;
    }

    private Map<String, Object> getRealmDetails(String token) throws JsonProcessingException
    {
        Map<String, Object> realmDetails = new HashMap<>();
        var client = webClientWrapper.createWebClient(token);
        String response = webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext(),GET,null);
        if (StringUtils.isNotEmpty(response))
        {
            realmDetails = this.objectMapper.readValue(response, new TypeReference<HashMap<String, Object>>() {});
        }
        if (realmDetails.containsKey(ERROR))
        {
            throw new RealmAcessException(UNABLE_TO_ACCESS_REALMS,globalMessageSource.get(UNABLE_TO_ACCESS_REALMS));
        }
        return realmDetails;
    }

    private Map<String, Object> getUserIdByUsername(String token, String username)
    {
        var client = webClientWrapper.createWebClient(token);
        String userDetails = webClientWrapper.webclientRequestForUser(client,
                keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi,
                username);
        var roleObject = new JSONTokener(userDetails).nextValue();
        if (roleObject instanceof JSONObject)
        {
            throw new InvalidInputException(UNABLE_TO_GET_USER_ROLES,globalMessageSource.get(UNABLE_TO_GET_USER_ROLES,roleObject));
        }
        JSONArray roleArray = (JSONArray) roleObject;
        if (roleArray.isEmpty())
        {
            throw new UserNotFoundException(USER_NOT_FOUND_EXCEPTION,globalMessageSource.get(USER_NOT_FOUND_EXCEPTION));
        }
        return roleArray.toList().stream().map(user ->
        {
            HashMap<String,String> userObject = this.objectMapper.convertValue(user,HashMap.class);
            Map<String, Object> userInfo = new HashMap<>();
            if (userObject.containsKey(ID))
            {
                userInfo.put(ID, userObject.get(ID));
                userInfo.put(USER_NAME, userObject.get("username"));
            }
            return userInfo;
        }).findFirst().orElse(null);
    }

    @Override
    public void deleteUser(String userName) throws JsonProcessingException
    {
        String user=URLDecoder.decode( userName, StandardCharsets.UTF_8);
        String token = tokenUtils.getTokenFromContext();
        Map<String, String> realmDetails;
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        Map<String, Object> userDetails = getUserIdByUsername(token,user);
        if (userDetails == null)
        {
            throw new UserNotFoundException(UNABLE_TO_FIND_USER,globalMessageSource.get(UNABLE_TO_FIND_USER));
        }
        var client = webClientWrapper.createWebClient(token);
        String response = webClientWrapper.webclientRequest(client,keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi +URL_SEPERATOR+ userDetails.get(ID),DELETE,null);
        if (StringUtils.isNotEmpty(response))
        {
            realmDetails = this.objectMapper
                    .readValue(response, new TypeReference<>()
                    {
                    });
            String error = realmDetails.values().stream().findFirst().orElseThrow(RuntimeException::new);
            throw new InvalidInputException(UNABLE_TO_DELETE_USER,globalMessageSource.get(UNABLE_TO_DELETE_USER,error));
        }
    }

    @Override
    public Stream<GroupsSaveSchema>  getAllGroups() throws JsonProcessingException
    {
        String token = tokenUtils.getTokenFromContext();
        List<String> defaultGroups = new ArrayList<>();
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        String response;
        Map<String, Object> realmDetails = getRealmDetails(token);
        if (realmDetails.containsKey(DEFAULT_GROUPS))
        {
            defaultGroups = (List<String>) realmDetails.get(DEFAULT_GROUPS);
        }
        response = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ getGroupsApi,GET,null);
        if (response == null || response.isEmpty())
        {
            throw new GroupsNotFoundException(UNABLE_TO_GET_GROUPS,globalMessageSource.get(UNABLE_TO_GET_GROUPS));
        }
        List<Map<String, Object>> groupsStream = this.objectMapper
                .readValue(response, new TypeReference<>()
                {
                });
        groupsStream.sort(Comparator.comparing(m -> m.get(GROUP_NAME).toString()));
        List<String> finalDefaultGroups = defaultGroups;
        return groupsStream.stream().map(groupDetails ->
        {
            var groupsSchema = this.objectMapper.convertValue(groupDetails, GroupsSaveSchema.class)
                    .withId(String.valueOf(groupDetails.get(ID)))
                    .withName(String.valueOf(groupDetails.get(GROUP_NAME)));
            if (finalDefaultGroups.isEmpty() || !finalDefaultGroups.contains(groupsSchema.getName()))
                return groupsSchema;
            return null;
        }).filter(Objects::nonNull);

    }

    @Override
    public GroupsSchema getGroupById(String id) throws JsonProcessingException
    {
        String token= tokenUtils.getTokenFromContext();
        List<String> defaultGroups = new ArrayList<>();
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        Map<String, Object> realmDetails = getRealmDetails(token);
        if (realmDetails.containsKey(DEFAULT_GROUPS))
        {
            defaultGroups = (List<String>) realmDetails.get(DEFAULT_GROUPS);
        }
        String response = webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext() + getGroupsApi +URL_SEPERATOR + id,GET,null);
        if (response == null || response.isEmpty())
        {
            throw new GroupsNotFoundException(UNABLE_TO_GET_GROUPS_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUPS_WITH_ID));
        }
        if (response.contains(ERROR))
        {
            throw new GroupsNotFoundException(UNABLE_TO_GET_GROUPS_WITH_ID,globalMessageSource.get(UNABLE_TO_GET_GROUP_WITH_ID));
        }
        Map<String, Object> group = this.objectMapper
                .readValue(response, new TypeReference<>()
                {
                });
        List<String> finalDefaultGroups = defaultGroups;

        var groupsSchema = this.objectMapper.convertValue(group, GroupsSchema.class)
                .withId(String.valueOf(group.get(ID)))
                .withName(String.valueOf(group.get(GROUP_NAME)));
        if (finalDefaultGroups.isEmpty() || !finalDefaultGroups.contains(groupsSchema.getName()))
            return groupsSchema;
        return null;
    }

    @Override
    public List<GroupsSaveSchema> createGroup(GroupsSchema groupsSchema) throws JsonProcessingException
    {
        String token= tokenUtils.getTokenFromContext();
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        if (StringUtils.isNotEmpty(groupsSchema.getId()))
        {
            String id = groupsSchema.getId();
            Stream<GroupsSaveSchema> groupsStream = getAllGroups();
            List<GroupsSaveSchema> update = groupsStream.filter(groupDetails ->
                    !groupDetails.getId().equals(id) && groupDetails.getName().equals(groupsSchema.getName())).collect(Collectors.toList());
            if (!update.isEmpty())
            {
                throw new InvalidInputException(UNABLE_TO_ADD_GROUP,globalMessageSource.get(UNABLE_TO_ADD_GROUP));
            }
            var groupSchemaPost = this.objectMapper.convertValue(groupsSchema, SaveSchema.class)
                    .withName(groupsSchema.getName());
            webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext() + getGroupsApi +URL_SEPERATOR + id,PUT,groupSchemaPost);
            Stream<GroupsSaveSchema> groupsSchemas = getAllGroups();
            return groupsSchemas.filter(groupDetails -> groupDetails.getId().equals(groupsSchema.getId())).collect(Collectors.toList());
        }
        Stream<GroupsSaveSchema> groupsSchemas = getAllGroups();
        List<GroupsSaveSchema> returnSchema = groupsSchemas.filter(groupDetails -> groupDetails.getName().equals(groupsSchema.getName())).collect(Collectors.toList());
        if (!returnSchema.isEmpty())
        {
            throw new InvalidInputException(UNABLE_TO_ADD_GROUP,globalMessageSource.get(UNABLE_TO_ADD_GROUP));
        }
        var groupSchemaPost = this.objectMapper.convertValue(groupsSchema, SaveSchema.class)
                .withName(groupsSchema.getName());
        webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext() + getGroupsApi,POST,groupSchemaPost);
        Stream<GroupsSaveSchema> savedGroupsSchemas = getAllGroups();
        return savedGroupsSchemas.filter(groupDetails -> groupDetails.getName().equals(groupsSchema.getName())).collect(Collectors.toList());
    }

    @Override
    public void deleteGroup(String id)
    {
        String token= tokenUtils.getTokenFromContext();
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        webClientWrapper.webclientRequest(client, keyCloakApi+ tokenUtils.getIssuerFromContext() + getGroupsApi +URL_SEPERATOR + id,DELETE,id);
    }

    @Override
    public void assignUserGroup(UserGroupsSchema userGroupsSchema) throws JsonProcessingException
    {
        String token = tokenUtils.getTokenFromContext();
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        List<GetUserGroupSchema> deleteGroups = getUserGroups(token, userGroupsSchema.getUserId());
        if (!deleteGroups.isEmpty())
        {
            deleteUserGroups(token, userGroupsSchema.getUserId(), deleteGroups);
        }
        assignGroup(token, userGroupsSchema);
    }

    private List<GetUserGroupSchema> getUserGroups(String token, String userId) throws JsonProcessingException
    {
        List<String> defaultGroups = new ArrayList<>();
        var client = webClientWrapper.createWebClient(token);
        Map<String, Object> realmDetails = getRealmDetails(token);
        if (realmDetails.containsKey(DEFAULT_GROUPS))
        {
            defaultGroups = (List<String>) realmDetails.get(DEFAULT_GROUPS);
        }
        String response = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi + URL_SEPERATOR + userId + getGroupsApi,GET,null);
        var groupObject = new JSONTokener(response).nextValue();
        if (groupObject instanceof JSONObject)
        {
            throw new InvalidInputException(UNABLE_TO_GET_USER_GROUPS,globalMessageSource.get(UNABLE_TO_GET_USER_GROUPS,groupObject));
        }
        List<String> finalDefaultGroups = defaultGroups;
        return ((JSONArray) groupObject).toList().stream().map(groupDetails ->
        {
            HashMap<String,String> groups = this.objectMapper.convertValue(groupDetails,HashMap.class);
            var groupSchema = this.objectMapper.convertValue(groups, GetUserGroupSchema.class)
                    .withGroupId(groups.get(ID))
                    .withGroupName(groups.get(GROUP_NAME));
            if (finalDefaultGroups.isEmpty() || !finalDefaultGroups.contains(groupSchema.getGroupName()))
                return groupSchema;
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void deleteUserGroups(String token, String userId, List<GetUserGroupSchema> deleteGroups)
    {
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        for (GetUserGroupSchema group : deleteGroups)
        {
            String groupId = String.valueOf(group.getGroupId());
            String groupResponse = webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtils.getIssuerFromContext() + userCreationApi +URL_SEPERATOR+ userId + getGroupsApi +URL_SEPERATOR+ groupId,DELETE,null);
            if (StringUtils.isNotEmpty(groupResponse))
                throw new InvalidInputException(UNABLE_TO_DELETE_GROUPS,globalMessageSource.get(UNABLE_TO_DELETE_GROUPS));
        }
    }

    private void assignGroup(String token, UserGroupsSchema userGroupsSchema) throws JsonProcessingException
    {
        Map<String, String> realmDetails;
        if (StringUtils.isEmpty(token))
        {
            throw new InvalidInputException(TOKEN_NOT_FOUND,globalMessageSource.get(TOKEN_NOT_FOUND));
        }
        var client = webClientWrapper.createWebClient(token);
        Stream<GroupsSaveSchema> groups = getAllGroups();
        List<Map<String, Object>> addGroupList = groups.map(group ->
        {
            if (userGroupsSchema.getGroups().contains(group.getName()))
            {
                Map<String, Object> addGroup = new HashMap<>();
                addGroup.put(ID, group.getId());
                addGroup.put(GROUP_NAME, group.getName());
                return addGroup;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        if(!userGroupsSchema.getGroups().isEmpty()&&addGroupList.isEmpty())
        {
            throw new GroupsNotFoundException(GROUP_WITH_THIS_NAME_NOT_FOUND,globalMessageSource.get(GROUP_WITH_THIS_NAME_NOT_FOUND));
        }
        String groupResponse = null;
        for (Map<String, Object> groupList : addGroupList)
        {
            groupResponse = webClientWrapper.webclientRequest(client,keyCloakApi+ tokenUtils.getIssuerFromContext() + userCreationApi + URL_SEPERATOR + userGroupsSchema.getUserId() + getGroupsApi +URL_SEPERATOR+ groupList.get(ID),PUT,groupList.get(ID));
        }
        if (StringUtils.isNotEmpty(groupResponse))
        {
            realmDetails = this.objectMapper
                    .readValue(groupResponse, new TypeReference<>()
                    {
                    });
            String error = realmDetails.values().stream().findFirst().orElseThrow(RuntimeException::new);
            throw new InvalidInputException(UNABLE_TO_ADD_ROLE,globalMessageSource.get(UNABLE_TO_ADD_ROLE,error));
        }
    }

    private static String generatePassword()
    {
        int len = 8;
        String smallChars = SMALL_CHARS;
        String values = CAPITAL_CHARS + smallChars +
                NUMBERS;
        var password = new StringBuilder(8);
        password.append(smallChars.charAt(randomMethod.nextInt(smallChars.length())));
        for (var i = 1; i < len; i++)
        {
            password.append
                    (
                            values.charAt(randomMethod.nextInt(values.length())));
        }
        return password.toString();
    }

    public void changePassword() throws JsonProcessingException
    {
        Map<String,Object> loggedInUser = userServiceImpl.getCurrentlyLoggedInUserId().get(0);
        String userName=loggedInUser.get(USER_NAME_DATA).toString();
        Map<String, Object> userDetails = getUserIdByUsername(tokenUtils.getTokenFromContext(),userName);
        if (userDetails == null)
        {
            throw new UserNotFoundException(UNABLE_TO_FIND_USER,globalMessageSource.get(UNABLE_TO_FIND_USER));
        }
        var client = webClientWrapper.createWebClient(tokenUtils.getTokenFromContext());
        String response = webClientWrapper.webclientRequest(client,
                keyCloakApi+ tokenUtils.getIssuerFromContext() + userCreationApi+SLASH+userDetails.get(ID)+EXEC_ACTIONS+emailValidity,PUT,List.of(USER_SCHEMA_UPDATE_PASSWORD));
        if(response!=null)
        {
            throw new MailException(UNABLE_TO_SEND_EMAIL,globalMessageSource.get(UNABLE_TO_SEND_EMAIL));
        }
    }
    public  Map<String,Object> setPassword(String userName) throws JsonProcessingException
    {
        String user=URLDecoder.decode( userName, StandardCharsets.UTF_8);
        String token= tokenUtils.getTokenFromContext();
        Map<String,Object> userDetails=getUserIdByUsername(token,user);
        Map<String, Object> passwordCred = new HashMap<>();
        String password=generatePassword();
        passwordCred.put(USER_SCHEMA_TYPE,USER_SCHEMA_PASSWORD);
        passwordCred.put(USER_SCHEMA_VALUE, password);
        passwordCred.put(USER_SCHEMA_TEMPORARY, false);
        if (userDetails == null)
        {
            throw new UserNotFoundException(UNABLE_TO_FIND_USER,globalMessageSource.get(UNABLE_TO_FIND_USER));
        }
        String response = webClientWrapper.webclientRequest(webClientWrapper.createWebClient(token), keyCloakApi + tokenUtils.getIssuerFromContext()+ userCreationApi +URL_SEPERATOR+ userDetails.get(ID)+URL_SEPERATOR+"reset-password",PUT,passwordCred);
        if (StringUtils.isNotEmpty(response))
        {
            Map<String,String> errorDetails = this.objectMapper
                    .readValue(response, new TypeReference<>()
                    {
                    });
            String error = errorDetails.values().stream().findFirst().orElseThrow(RuntimeException::new);
            throw new InvalidInputException(UNABLE_TO_SET_PASSWORD,globalMessageSource.get( UNABLE_TO_SET_PASSWORD,error));
        }
        userDetails.put(USER_SCHEMA_PASSWORD,password);
        return userDetails;
    }

    @Override
    public String addRoles(String clientName, RolesDto rolesDto)  {
        try {
            String token = tokenUtils.getTokenFromContext();
            var client = webClientWrapper.createWebClient(token);
            String data = webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromToken(tokenUtils.getTokenFromContext()) + GET_ALL_CLIENTS_URL, GET, null);
            List<Map<String, Object>> clients = this.objectMapper.readValue(data, List.class);
            String id = clients.stream().filter(e -> e.get("clientId").toString().equalsIgnoreCase(clientName)).collect(Collectors.toList()).get(0).get("id").toString();
            return webClientWrapper.webclientRequest(client, keyCloakApi + tokenUtils.getIssuerFromToken(tokenUtils.getTokenFromContext()) + GET_CLIENT_ROLES_URL + id + ROLES_URL, POST, rolesDto);
        }
        catch (Exception e)
        {
            throw new RunTimeException(e.getMessage());
        }
    }
}
