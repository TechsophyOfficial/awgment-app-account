package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.MenuRoleAssignSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.entity.MenuRoleAssignDefinition;
import com.techsophy.tsf.account.repository.MenuRoleAssignRepository;
import com.techsophy.tsf.account.service.impl.MenuRoleAssignServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigInteger;
import java.util.*;
import static com.techsophy.tsf.account.constants.UserPreferencesConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
 class MenuRoleAssignServiceImplTest
{
    @Mock
    MenuRoleAssignRepository mockMenuRoleAssignRepository;
    @Mock
    UserDetails mockUserDetails;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    IdGeneratorImpl mockIdGeneratorImpl;
    @Mock
    GlobalMessageSource mockGlobalMessageSource;
    @Mock
    TokenUtils mockTokenUtils;

    @Mock
    MenuService mockMenuService;

    @InjectMocks
    MenuRoleAssignServiceImpl mockMenuRoleAssignServiceImpl ;//= new MenuRoleAssignServiceImpl(mockMenuRoleAssignRepository,mockMenuService,mockObjectMapper,mockIdGeneratorImpl,mockGlobalMessageSource,mockUserDetails,mockTokenUtils);

    List<Map<String, Object>> userList = new ArrayList<>();
    List<String> menus = new ArrayList<>();

    @BeforeEach
    public void init()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(CREATED_BY_ID, NULL);
        map.put(CREATEDBYNAME, NULL);
        map.put(CREATEDON, NULL);
        map.put(UPDATEDBYID, NULL);
        map.put(UPDATEDBYNAME, NULL);
        map.put(UPDATED_ON, NULL);
        map.put(ID, ID_NUMBER);
        map.put(USER_NAME, TEJASWINI);
        map.put(FIRST_NAME, KAZA);
        map.put(LAST_NAME, TEJASWINI);
        map.put(MOBILE_NUMBER, NUMBER);
        map.put(EMAIL_ID, TEJASWINI_MAIL_ID);
        map.put(DEPARTMENT, NULL);
        userList.add(map);

        menus.add("abc");
        menus.add("bcd");

    }

    @Test
    void saveMenuRoleTest() throws JsonProcessingException {

        MenuRoleAssignSchema menuRoleAssignSchema = new MenuRoleAssignSchema(String.valueOf(BigInteger.valueOf(1)),"role",menus);
        MenuRoleAssignSchema menuRoleAssignSchema1 = new MenuRoleAssignSchema(null,"role",menus);
        MenuRoleAssignDefinition menuRoleAssignDefinition = new MenuRoleAssignDefinition(BigInteger.valueOf(10),"role",menus,1);
        MenuRoleAssignDefinition menuRoleAssignDefinition1 = new MenuRoleAssignDefinition(null,"role",menus,2);
        when(mockObjectMapper.convertValue(any(),eq(MenuRoleAssignDefinition.class))).thenReturn(menuRoleAssignDefinition).thenReturn(menuRoleAssignDefinition1);
        when(mockUserDetails.getUserDetails()).thenReturn(userList);
        when(mockIdGeneratorImpl.nextId()).thenReturn(BigInteger.valueOf(Long.parseLong(USER_ID)));
        when(mockMenuRoleAssignRepository.save(any())).thenReturn(menuRoleAssignDefinition);
        when(mockMenuRoleAssignRepository.findById(Long.parseLong("1"))).thenReturn(Optional.of(menuRoleAssignDefinition));
        mockMenuRoleAssignServiceImpl.saveMenuRole(menuRoleAssignSchema1);
        mockMenuRoleAssignServiceImpl.saveMenuRole(menuRoleAssignSchema);
        verify(mockMenuRoleAssignRepository, times(2)).save(any());
    }
    @Test
    void getMenuRoleTest(){
        MenuRoleAssignSchema menuRoleAssignSchema = new MenuRoleAssignSchema(String.valueOf(BigInteger.valueOf(1)),"role",menus);
        MenuRoleAssignDefinition menuRoleAssignDefinition = new MenuRoleAssignDefinition(BigInteger.valueOf(10),"role",menus,1);
        when(mockObjectMapper.convertValue(any(),eq(MenuRoleAssignSchema.class))).thenReturn(menuRoleAssignSchema);
        when(mockMenuRoleAssignRepository.findById(BigInteger.valueOf(Long.parseLong("10")))).thenReturn(Optional.of(menuRoleAssignDefinition));
        mockMenuRoleAssignServiceImpl.getMenuRole("10");
        verify(mockMenuRoleAssignRepository,times(1)).findById((BigInteger) any());
    }
    
    @Test
    void getAllMenuRoleTest(){
        MenuRoleAssignDefinition menuRoleAssignDefinition = new MenuRoleAssignDefinition(BigInteger.valueOf(10),"role",menus,1);
        MenuRoleAssignSchema menuRoleAssignSchema = new MenuRoleAssignSchema(String.valueOf(BigInteger.valueOf(1)),"role",menus);
        when(mockMenuRoleAssignRepository.findAll()).thenReturn(List.of(menuRoleAssignDefinition));
        mockMenuRoleAssignServiceImpl.getAllMenuRole();
        verify(mockMenuRoleAssignRepository,times(1)).findAll();
    }

    @Test
    void deleteMenuRoleByIdTest(){
        when(mockMenuRoleAssignRepository.existsById(BigInteger.valueOf(1))).thenReturn(true).thenReturn(false);
        doNothing().when(mockMenuRoleAssignRepository).deleteById(BigInteger.valueOf(1));
        mockMenuRoleAssignServiceImpl.deleteMenuRoleById("1");
        verify(mockMenuRoleAssignRepository,times(1)).existsById(BigInteger.valueOf(1));
    }

    @Test
    void getAssignedMenuToUserRolesTest(){
        MenuSchema menuSchema = new MenuSchema();
        MenuRoleAssignDefinition menuRoleAssignDefinition = new MenuRoleAssignDefinition(BigInteger.valueOf(10),"role",menus,1);
        when(mockTokenUtils.getTokenFromContext()).thenReturn(String.valueOf(menus));
        when(mockTokenUtils.getClientRoles(any())).thenReturn(menus);
        when(mockMenuRoleAssignRepository.findByRole(any())).thenReturn(menuRoleAssignDefinition);
        when(mockMenuService.getMenuById(any())).thenReturn(menuSchema);
        mockMenuRoleAssignServiceImpl.getAssignedMenuToUserRoles();
        verify(mockMenuRoleAssignRepository,times(1)).findByRole("abc");
    }
}
