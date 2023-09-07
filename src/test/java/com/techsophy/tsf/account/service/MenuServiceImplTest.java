package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.entity.MenuDefinition;
import com.techsophy.tsf.account.repository.MenuRepository;
import com.techsophy.tsf.account.service.impl.MenuServiceImpl;
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
 class MenuServiceImplTest
{
    @Mock
    MenuRepository mockMenuRepository;
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
    MenuServiceImpl mockMenuServiceImpl;

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
    void saveMenuTest() throws JsonProcessingException {

        MenuSchema menuSchema = new MenuSchema(String.valueOf(BigInteger.valueOf(10)),"type","label","url",true,"version");
        MenuSchema menuSchema1 = new MenuSchema(null,"type","label","url",true,"version");
        MenuDefinition menuDefinition = new MenuDefinition(BigInteger.valueOf(10),"type","label","url",true, 2);
        MenuDefinition menuDefinition1 = new MenuDefinition(null,"type1","label1","url1",false,3);
        when(mockObjectMapper.convertValue(any(),eq(MenuDefinition.class))).thenReturn(menuDefinition).thenReturn(menuDefinition1);
        when(mockUserDetails.getUserDetails()).thenReturn(userList);
        when(mockIdGeneratorImpl.nextId()).thenReturn(BigInteger.valueOf(Long.parseLong(USER_ID)));
        when(mockMenuRepository.save(any())).thenReturn(menuDefinition);
        when(mockMenuRepository.findById(BigInteger.valueOf(Long.valueOf(10)))).thenReturn(Optional.of(menuDefinition));
        mockMenuServiceImpl.saveMenu(menuSchema);
        mockMenuServiceImpl.saveMenu(menuSchema1);
        verify(mockMenuRepository, times(2)).save(any());
    }

    @Test
    void getMenuTest(){
        MenuSchema menuSchema = new MenuSchema(String.valueOf(BigInteger.valueOf(1)),"type","label","url",true,"version");
        MenuDefinition menuDefinition = new MenuDefinition(BigInteger.valueOf(10),"type","label","url",true,2);
        when(mockObjectMapper.convertValue(any(),eq(MenuSchema.class))).thenReturn(menuSchema);
        when(mockMenuRepository.findById(BigInteger.valueOf(Long.parseLong("10")))).thenReturn(Optional.of(menuDefinition));
        mockMenuServiceImpl.getMenuById("10");
        verify(mockMenuRepository,times(1)).findById((BigInteger) any());
    }

    @Test
    void getAllMenuTest()
    {
        MenuDefinition menuDefinition = new MenuDefinition(BigInteger.valueOf(10),"type","label","url",true,2);
        when(mockMenuRepository.findAll()).thenReturn(List.of(menuDefinition));
        mockMenuServiceImpl.getAllMenus();
        verify(mockMenuRepository,times(1)).findAll();
    }

    @Test
    void deleteMenuRoleById(){
        when(mockMenuRepository.existsById(BigInteger.valueOf(1))).thenReturn(true).thenReturn(false);
        doNothing().when(mockMenuRepository).deleteById(BigInteger.valueOf(1));
        mockMenuServiceImpl.deleteMenuById("1");
        verify(mockMenuRepository,times(1)).existsById(BigInteger.ONE);
    }
}
