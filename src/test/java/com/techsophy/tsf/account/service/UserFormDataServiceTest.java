package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionRepository;
import com.techsophy.tsf.account.service.impl.UserFormDataServiceImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.Cleanup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

import static com.techsophy.tsf.account.constants.AccountConstants.USER_ID;
import static com.techsophy.tsf.account.constants.UserConstants.USER_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserFormDataServiceTest
{
    @Mock
    UserFormDataDefinitionRepository mockUserFormDataDefinitionRepository;
    @Mock
    UserServiceImpl mockUserServiceImpl;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl mockIdGenerator;
    @Mock
    TokenUtils accountUtils;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    TokenUtils mockUtilityService;
    @InjectMocks
    UserFormDataServiceImpl userFormDataService;
    @Mock
    AuditableData auditableData;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    UserDetails mockUserDetails;
    UserDefinition userDefinition=new UserDefinition();

    private List<Map<String,Object>> list = new ArrayList<>();
     private Map<String,Object> map = new HashMap<>();
    @BeforeEach
    public void init()
    {
        list = new ArrayList<>();map = new HashMap<>();
        map.put("abc","abc");
        map.put("id",1);
        map.put("userId",1);
        map.put("userName","Nandini");
        list.add(map);
        userDefinition.setFirstName(USER_STRING);
        userDefinition.setCreatedById(BigInteger.valueOf(234234234));
        userDefinition.setId(BigInteger.valueOf(345345));
    }
    private static final String USER_DATA = "testdata/user-data.json";

    @Test
    void saveUserTest() throws IOException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        InputStream stream = new ClassPathResource(USER_DATA).getInputStream();
        String userData= new String(stream.readAllBytes());
        UserFormDataSchema userFormDataSchema= objectMapper.readValue(userData,UserFormDataSchema.class);
        UserFormDataSchema userFormDataSchema1 = new UserFormDataSchema(map,null,"1");
        UserFormDataDefinition userFormDataDefinition = new UserFormDataDefinition(BigInteger.valueOf(1),map,BigInteger.valueOf(1),1);
        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition(null,map,null,1);
        UserData userSchema=objectMapper.convertValue(userFormDataSchema.getUserData(), UserData.class);
        UserDefinition userDefinition=new UserDefinition();
        userDefinition.setFirstName("user");
        userDefinition.setCreatedById(BigInteger.valueOf(234234234));
        userDefinition.setId(BigInteger.valueOf(345345));
        when(mockUserServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        when(mockUserServiceImpl.saveUser(any())).thenReturn(userDefinition.withId(BigInteger.valueOf(1234)));
        when(mockObjectMapper.convertValue(any(),eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition).thenReturn(userFormDataDefinition1);
        when(mockObjectMapper.convertValue(any(),eq(UserFormDataSchema.class))).thenReturn(userFormDataSchema1);
        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userSchema);
        when(mockObjectMapper.convertValue(userFormDataDefinition,UserFormDataSchema.class)).thenReturn(userFormDataSchema1);
        when(mockUserFormDataDefinitionRepository.save(any())).thenReturn(userFormDataDefinition);
        userFormDataService.saveUserFormData(userFormDataSchema1);
        verify(mockUserFormDataDefinitionRepository,times(1)).save(any());
    }

    @Test
    void updateUserTest() throws IOException
    {
        UserFormDataSchema userFormDataSchema=new UserFormDataSchema(map,"12345","2");
        when(mockUserServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        UserFormDataDefinition userFormDataDefinition=new UserFormDataDefinition();
        userFormDataDefinition.setUserData(map);
        userFormDataDefinition.setUserId(BigInteger.valueOf(12345));
        userFormDataDefinition.setVersion(1);
        when(mockUserServiceImpl.saveUser(any())).thenReturn(userDefinition.withId(BigInteger.valueOf(1234)));
        when(mockObjectMapper.convertValue(any(),eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition);
        UserData userData=new UserData();
        userData.setUserName("akhil");
        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userData);
        when(mockUserFormDataDefinitionRepository.findByUserId(any())).thenReturn(Optional.of(userFormDataDefinition));
        userFormDataService.saveUserFormData(userFormDataSchema);
        verify(mockUserFormDataDefinitionRepository,times(1)).save(any());
    }
    @Test
    void saveUserTestWithName() throws IOException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        InputStream stream = new ClassPathResource(USER_DATA).getInputStream();
        String userData= new String(stream.readAllBytes());
        UserFormDataSchema userFormDataSchema= objectMapper.readValue(userData,UserFormDataSchema.class);
        UserFormDataSchema userFormDataSchema1 = new UserFormDataSchema(map,"1","1");
        UserFormDataDefinition userFormDataDefinition = new UserFormDataDefinition(BigInteger.valueOf(1),map,BigInteger.valueOf(1),1);
        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition(null,map,null,1);
        UserData userSchema=objectMapper.convertValue(userFormDataSchema.getUserData(), UserData.class);
        UserDefinition userDefinition=new UserDefinition();
        userDefinition.setFirstName("user");
        userDefinition.setCreatedById(BigInteger.valueOf(234234234));
        userDefinition.setId(BigInteger.valueOf(345345));
        when(mockUserServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        when(mockUserServiceImpl.saveUser(any())).thenReturn(userDefinition.withId(BigInteger.valueOf(1234)));
        when(mockObjectMapper.convertValue(any(),eq(UserFormDataDefinition.class))).thenReturn(userFormDataDefinition).thenReturn(userFormDataDefinition1);
        when(mockObjectMapper.convertValue(any(),eq(UserFormDataSchema.class))).thenReturn(userFormDataSchema1);
        when(mockObjectMapper.convertValue(any(),eq(UserData.class))).thenReturn(userSchema);
        when(mockObjectMapper.convertValue(userFormDataDefinition,UserFormDataSchema.class)).thenReturn(userFormDataSchema1);
        when(mockUserFormDataDefinitionRepository.save(any())).thenReturn(userFormDataDefinition);
        UserFormDataSchema response=userFormDataService.saveUserFormData(userFormDataSchema);
        ArgumentCaptor<UserFormDataDefinition> argumentCaptor = ArgumentCaptor.forClass(UserFormDataDefinition.class);
        verify(mockUserFormDataDefinitionRepository).save(argumentCaptor.capture());
        Assertions.assertEquals("nandini",argumentCaptor.getValue().getUserData().get("userName"));
        Assertions.assertNotEquals("Nandini", response.getUserData().get("userName"));
    }


    @Test
    void getFormByUserId() throws IOException
    {
        ObjectMapper objectMapper=new ObjectMapper();
        @Cleanup InputStream stream = new ClassPathResource(USER_DATA).getInputStream();
        String userData= new String(stream.readAllBytes());
        UserFormDataSchema userFormDataSchema= objectMapper.readValue(userData,UserFormDataSchema.class);
        UserFormDataDefinition userFormDataDefinition= objectMapper.readValue(userData,UserFormDataDefinition.class);
        when(mockUserFormDataDefinitionRepository.findByUserId(any())).thenReturn(Optional.ofNullable(userFormDataDefinition));
        when(mockObjectMapper.convertValue(any(),eq(UserFormDataSchema.class))).thenReturn(userFormDataSchema);
        UserFormDataSchema response= (UserFormDataSchema) userFormDataService.getUserFormDataByUserId("1234",false);
        userFormDataService.getUserFormDataByUserId("1234",true);
        assertThat(response.getUserData()).isEqualTo(userFormDataSchema.getUserData());
    }

    @Test
    void getAllUserFormDataObjects()
    {
        UserData userSchema = new UserData("1","name","firstname","lastname","mobile","email","departmnt");
        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition(null,map,null,1);
        when(mockUserServiceImpl.getAllUsers(anyString(), (Sort) any())).thenReturn(List.of(userSchema));
        when(mockUserFormDataDefinitionRepository.findAll((Sort) any())).thenReturn(List.of(userFormDataDefinition1));
        when(mockUserFormDataDefinitionRepository.findFormDataUserByQSort(anyString(),any())).thenReturn(List.of(userFormDataDefinition1));
        List  response =  userFormDataService.getAllUserFormDataObjects(false,"abc",Sort.by("abc"));
        userFormDataService.getAllUserFormDataObjects(false,"",Sort.by("abc"));
        userFormDataService.getAllUserFormDataObjects(true,"abc",Sort.by("abc"));
        assertThat(response).isNotNull();
    }
    @Test
    void getAllUserFormDataObjectsPagination()
    {
        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition(null,map,null,1);
        Pageable pageable = PageRequest.of(1,1);
        Page page = new PageImpl(List.of(userFormDataDefinition1));
        when(mockObjectMapper.convertValue(any(),eq(Map.class))).thenReturn(map);
        when(mockUserFormDataDefinitionRepository.findAll(pageable)).thenReturn(page);
        List<UserFormDataDefinition> userFormDataDefinitionList=new ArrayList<>();
        userFormDataDefinitionList.add(userFormDataDefinition1);
        Mockito.when(mockUserFormDataDefinitionRepository.findFormDataUserByQPageable(anyString(),any())).thenReturn(new PageImpl<>(userFormDataDefinitionList,PageRequest.of(1,5),100));
        userFormDataService.getAllUserFormDataObjects(false,"",pageable);
        userFormDataService.getAllUserFormDataObjects(false,"abc",pageable);
        userFormDataService.getAllUserFormDataObjects(true,"abc",pageable);
        verify(mockUserFormDataDefinitionRepository,times(1)).findAll(pageable);
    }

    @Test
    void getAllUsersByFilterPagination()
    {
        UserFormDataDefinition userFormDataDefinition1 = new UserFormDataDefinition(BigInteger.valueOf(101),map,null,1);
        Pageable pageable = PageRequest.of(1,1);
        Page page = new PageImpl(List.of(userFormDataDefinition1));
        when(mockUserFormDataDefinitionRepository.findByFilterColumnAndValue(anyString(),anyString(),any(),anyString())).thenReturn(page);
        Map<String,Object> userData=new HashMap();
        userData.put("id",101);
        userData.put(USER_ID,102);
        Mockito.when(mockObjectMapper.convertValue(any(),eq(Map.class))).thenReturn(userData);
        userFormDataService.getAllUsersByFilter(false,"abc","abc",pageable,"q");
        userFormDataService.getAllUsersByFilter(true,"abc","abc",pageable,"q");
        verify(mockUserFormDataDefinitionRepository,times(2)).findByFilterColumnAndValue(anyString(),anyString(),any(),anyString());
    }

    @Test
    void deleteFormByUserIdTest()
    {
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        map.put("id",1);
        list.add(map);
        doNothing().when(mockUserServiceImpl).deleteUserById("123");
        userFormDataService.deleteUserFormDataByUserId("123");
        verify(mockUserServiceImpl, times(1)).deleteUserById("123");
    }

    @Test
    void getAllUsersByFilterListTest()
    {
        Map<String,Object> map = new HashMap<>();
        map.put("id","userId");
        map.put("userId","userId");
        when(mockUserServiceImpl.getAllUsersByFilter(any(),any())).thenReturn(List.of(auditableData));
        userFormDataService.getAllUsersByFilter(false,"abc","abc", (Sort) null,"");
        userFormDataService.getAllUsersByFilter(true,"abc","abc", (Sort) null,"q");
        verify(mockUserServiceImpl,times(1)).getAllUsersByFilter(any(),any());
    }
}
