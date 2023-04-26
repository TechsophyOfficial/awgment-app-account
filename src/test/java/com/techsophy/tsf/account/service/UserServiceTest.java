package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.ThemesConstants;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.PaginationResponsePayload;
import com.techsophy.tsf.account.dto.UserData;
import com.techsophy.tsf.account.dto.UserPreferencesSchema;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.exception.EntityNotFoundByIdException;
import com.techsophy.tsf.account.repository.UserDefinitionRepository;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigInteger;
import java.util.*;

import static com.techsophy.tsf.account.constants.AccountConstants.DEFAULT_THEME_ID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
 class UserServiceTest {
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl mockIdGenerator;
    @Mock
    TokenUtils accountUtils;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    UserPreferencesThemeService userPreferencesThemeService;
    @Mock
    UserDefinitionRepository accountRepository;
    @InjectMocks
    UserServiceImpl mockUserServiceImpl;
    @Mock
    UserDetails mockUserDetails;

    List<Map<String,Object>> list = new ArrayList<>();
    Map<String,Object> map = new HashMap<>();
    @BeforeEach
    public void init()
    {
        list = new ArrayList<>();
        map = new HashMap<>();
        map.put("firstname","nandini");
        map.put("id",1);
        map.put("lastname","nandini");
        list.add(map);
    }
    @Test
    void saveUserWithIdTest() {
        UserData userSchema = new UserData("1","name","name","last","12","ab","cse");
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        UserDefinition userDefinition1 = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        Mockito.when(accountRepository.findById(BigInteger.ONE)).thenReturn(Optional.of(userDefinition)).thenReturn(Optional.of(userDefinition));
        Mockito.when(accountRepository.save(any())).thenReturn(userDefinition.withId(BigInteger.valueOf(Long.parseLong(ThemesConstants.ID))));
        Mockito.when(mockObjectMapper.convertValue(any(),eq(UserDefinition.class))).thenReturn(userDefinition).thenReturn(userDefinition1);
        UserDefinition userDefinition2 = mockUserServiceImpl.saveUser(userSchema);
        Assertions.assertEquals(userDefinition,userDefinition2);
    }

    @Test
    void saveUserWithNullIdTest(){
        UserData userSchema = new UserData(null,"name","name","last","12","ab","cse");
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        Mockito.when(mockObjectMapper.convertValue(any(),eq(UserDefinition.class))).thenReturn(userDefinition).thenReturn(userDefinition);
        Mockito.when(accountRepository.existsByUserName(any())).thenReturn(false);
        Mockito.when(accountRepository.existsByEmailId(any())).thenReturn(false);
        Mockito.when(mockIdGenerator.nextId()).thenReturn(BigInteger.ONE);
        Mockito.when(accountRepository.save(any())).thenReturn(userDefinition.withId(BigInteger.valueOf(Long.parseLong(ThemesConstants.ID))));
        UserPreferencesSchema userPreferencesSchema = new UserPreferencesSchema("1234","1",null,null);
        when(this.mockObjectMapper.convertValue(any(), eq(UserPreferencesSchema.class))).thenReturn(userPreferencesSchema);
        UserDefinition response = mockUserServiceImpl.saveUser(userSchema);
        Assertions.assertNotNull(response);
    }
    @Test
    void getUserById()
    {
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        Mockito.when(accountRepository.findById(BigInteger.valueOf(1))).thenReturn(Optional.of(userDefinition));
        AuditableData response = mockUserServiceImpl.getUserById("1");
        Assertions.assertNull(response);
    }
    @Test
    void getAllUsers()
    {
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        Mockito.when(accountRepository.findAllUsers((Sort) any())).thenReturn(List.of(userDefinition));
        Mockito.when(accountRepository.findUserByQSort(anyString(),any())).thenReturn(List.of(userDefinition));
        mockUserServiceImpl.getAllUsers("abc",Sort.by("email"));
        List<UserData> list = mockUserServiceImpl.getAllUsers("",Sort.by("email"));
        Assertions.assertNotNull(list);
    }
    @Test
    void getAllUsersPagination()
    {
        List<String>  list1 = new ArrayList<>();
        list1.add("id");
        PaginationResponsePayload paginationResponsePayload = new PaginationResponsePayload(list,1,1L,1,1,1);
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        Page<UserDefinition> page = new PageImpl<>(List.of(userDefinition));
        Pageable pageable = PageRequest.of(1,1);
        Mockito.when(accountRepository.findAllUsers(pageable)).thenReturn(page);
        Mockito.when(accountRepository.findUserByQPageable("abc",pageable)).thenReturn(page);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(Map.class))).thenReturn(map);
        Mockito.when(accountUtils.getPaginationResponsePayload(any(),any())).thenReturn(paginationResponsePayload);
        mockUserServiceImpl.getAllUsers("abc",pageable);
        mockUserServiceImpl.getAllUsers("",pageable);
        verify(accountRepository,times(1)).findAllUsers(pageable);
    }
    @Test
    void getUserByLoginId()
    {
        UserData userSchema = new UserData("1","name","name","last","12","ab","cse");
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        Mockito.when(accountRepository.findByEmailIdOrUserName("1","1")).thenReturn(Optional.of(userDefinition));
        Mockito.when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userSchema);
        UserData response = mockUserServiceImpl.getUserByLoginId("1");
        Assertions.assertNotNull(response);
    }
    @Test
    void deleteUserById()
    {
        Mockito.when(accountRepository.existsById(BigInteger.valueOf(1))).thenReturn(true).thenReturn(false);
        doNothing().when(accountRepository).deleteById(BigInteger.valueOf(1));
        mockUserServiceImpl.deleteUserById("1");
        Assertions.assertThrows(EntityNotFoundByIdException.class,()->mockUserServiceImpl.deleteUserById("1"));
    }
    @Test
    void getAllUsersByFilter()
    {
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,"abc","abc","abc","1","abc","abc");
        UserData userSchema = new UserData("1","name","name","last","12","ab","cse");
        Mockito.when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userSchema);
        Mockito.when(accountRepository.findByEmailIdOrUserName("SYSTEM","SYSTEM")).thenReturn(Optional.of(userDefinition));
        mockUserServiceImpl.getAllUsersByFilter("abc","service-account");
        List<AuditableData> list =mockUserServiceImpl.getAllUsersByFilter("LOGINID","service");
        Assertions.assertNotNull(list);
    }
}