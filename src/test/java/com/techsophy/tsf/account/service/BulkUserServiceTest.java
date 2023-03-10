package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.constants.ThemesConstants;
import com.techsophy.tsf.account.dto.*;
import com.techsophy.tsf.account.entity.BulkUserDefinition;
import com.techsophy.tsf.account.exception.BulkUserNotFoundException;
import com.techsophy.tsf.account.exception.FileNameNotPresentException;
import com.techsophy.tsf.account.exception.InvalidDataException;
import com.techsophy.tsf.account.repository.BulkUploadDefinintionRepository;
import com.techsophy.tsf.account.service.impl.BulkUserServiceImplementation;
import com.techsophy.tsf.account.service.impl.UserManagementInKeyCloakImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkUserServiceTest
{
    @Mock
    UserServiceImpl userServiceImpl;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl idGenerator;
    @Mock
    TokenUtils tokenUtils;
    @Mock
    TokenUtils accountUtils;
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    BulkUploadDefinintionRepository bulkUploadDefinintionRepository;
    @Mock
    UserManagementInKeyCloakImpl userManagementInKeyCloak;
    @InjectMocks
    BulkUserServiceImplementation bulkUserServiceImplementation;
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
    void bulkUploadUsers() throws Exception
    {
        when(idGenerator.nextId()).thenReturn(BigInteger.ONE);
        BulkUserDefinition bulkUserDefinition = new BulkUserDefinition(BigInteger.valueOf(1),map,BigInteger.valueOf(1),"abc");
        File file = new File("src/test/resources/testdata/accounts_template.csv");
        FileInputStream input = new FileInputStream(file);
        List<String> s = new ArrayList<>();
        s.add("abc");
        s.add("bcd");
        GroupsSaveSchema schema1 = new GroupsSaveSchema("WorkingGroup101","abc");
        RolesSchema rolesSchema = new RolesSchema("abc","abc");
        LinkedHashMap<String,Object> userDetails = new LinkedHashMap<>();
        userDetails.put(GROUPS,s);
        Mockito.when(userServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        MultipartFile multipartFile = new MockMultipartFile("file", file.getName(), "text/plain", IOUtils.toByteArray(input));
        when(this.objectMapper.convertValue(any(),ArgumentMatchers.eq(String.class))).thenReturn("abc,abc");
        Mockito.when(bulkUploadDefinintionRepository.save(any())).thenReturn(bulkUserDefinition);
        Mockito.when(userManagementInKeyCloak.getAllGroups()).thenReturn(Stream.of(schema1)).thenReturn(Stream.of(schema1)).thenReturn(Stream.of(schema1));
        Mockito.when(userManagementInKeyCloak.getAllRoles()).thenReturn(List.of(rolesSchema));
        Mockito.when(bulkUploadDefinintionRepository.findById(BigInteger.valueOf(1))).thenReturn(bulkUserDefinition);
        bulkUserServiceImplementation.bulkUploadUsers(multipartFile);
        Mockito.verify(bulkUploadDefinintionRepository,Mockito.times(1)).findById(BigInteger.valueOf(1));
    }

    @Test
    void bulkUploadFileNameExceptionTest() throws IOException
    {
        when(idGenerator.nextId()).thenReturn(BigInteger.ONE);
        BulkUserDefinition bulkUserDefinition = new BulkUserDefinition(BigInteger.valueOf(1),map,BigInteger.valueOf(1),"abc");
        File file = new File("src/test/resources/testdata/accounts_template.csv");
        FileInputStream input = new FileInputStream(file);
        List<String> s = new ArrayList<>();
        s.add("abc");
        s.add("bcd");
        GroupsSaveSchema schema1 = new GroupsSaveSchema("WorkingGroup101","abc");
        RolesSchema rolesSchema = new RolesSchema("abc","abc");
        LinkedHashMap<String,Object> userDetails = new LinkedHashMap<>();
        userDetails.put(GROUPS,s);
        Mockito.when(userServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        MultipartFile multipartFile = new MockMultipartFile("file1","", "text/plain", IOUtils.toByteArray(input));
        Assertions.assertThrows(FileNameNotPresentException.class,()->bulkUserServiceImplementation.bulkUploadUsers(multipartFile));
    }

    @Test
    void bulkUpdateStatus() throws Exception
    {
        Mockito.when(userServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        BulkUserDefinition bulkUserDefinition = new BulkUserDefinition(BigInteger.valueOf(1),map,BigInteger.valueOf(1),"status");
        BulkUploadSchema bulkUploadSchema = new BulkUploadSchema("1",map,"1","status");
        Mockito.when(bulkUploadDefinintionRepository.findById(BigInteger.valueOf(1))).thenReturn(bulkUserDefinition);
        Mockito.when(bulkUploadDefinintionRepository.save(any())).thenReturn(bulkUserDefinition.withId(BigInteger.valueOf(Long.parseLong(ThemesConstants.ID))));
        Mockito.when(bulkUploadDefinintionRepository.existsById(BigInteger.valueOf(1))).thenReturn(true);
        BulkUploadResponse bulkUploadResponse= bulkUserServiceImplementation.bulkUpdateStatus(bulkUploadSchema);
        Assertions.assertNotNull(bulkUploadResponse);
    }

    @Test
    void getAllBulkUsersTest()
    {
        Mockito.when(bulkUploadDefinintionRepository.findBulkUsersByFilter(any(),any())).thenReturn(List.of());
        bulkUserServiceImplementation.getAllBulkUsers("id","101","","");
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUsersByFilter(any(),any());
    }

    @Test
    void getAllBulkUsersPaginationEmptySortTest()
    {
        Page<BulkUserDefinition> bulkUserDefinitionList=new PageImpl<>(List.of());
        Mockito.when(bulkUploadDefinintionRepository.findBulkUserByFilterPageable(any(),any(),any())).thenReturn(bulkUserDefinitionList);
        bulkUserServiceImplementation.getAllBulkUsers("id","101","","",PageRequest.of(1,1));
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUserByFilterPageable(any(),any(),any());
    }

    @Test
    void getAllBulkUsersPaginationWithSortTest()
    {
        BulkUserDefinition bulkUserDefinition=new BulkUserDefinition();
        bulkUserDefinition.setId(BigInteger.valueOf(101));
        Page<BulkUserDefinition> bulkUserDefinitionList=new PageImpl<>(List.of(bulkUserDefinition));
        Mockito.when(bulkUploadDefinintionRepository.findBulkUserByFilterSortPageable(any(),any(),any(),any(),any())).thenReturn(bulkUserDefinitionList);
        bulkUserServiceImplementation.getAllBulkUsers("id","101","createdOn","desc",PageRequest.of(1,1));
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUserByFilterSortPageable(any(),any(),any(),any(),any());
    }

    @Test
    void getAllBulkUsersListEmptySortTest()
    {
        Mockito.when(bulkUploadDefinintionRepository.findBulkUsersByQ(any())).thenReturn(List.of());
        bulkUserServiceImplementation.getAllBulkUsers("101","","");
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUsersByQ(any());
    }

    @Test
    void getAllBulkUsersListWithSortTest()
    {
        Mockito.when(bulkUploadDefinintionRepository.findBulkUsersByQSort(any(),any(),any())).thenReturn(List.of());
        bulkUserServiceImplementation.getAllBulkUsers("101","createdOn","desc");
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUsersByQSort(any(),any(),any());
    }

    @Test
    void getAllBulkUsersPaginationQEmptySortTest()
    {
        BulkUserDefinition bulkUserDefinition=new BulkUserDefinition();
        bulkUserDefinition.setId(BigInteger.valueOf(101));
        Page<BulkUserDefinition> bulkUserDefinitionList=new PageImpl<>(List.of(bulkUserDefinition));
        Mockito.when(bulkUploadDefinintionRepository.findBulkUsersByQPageable(any(),any())).thenReturn(bulkUserDefinitionList);
        bulkUserServiceImplementation.getAllBulkUsers("101","","",PageRequest.of(1,1));
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUsersByQPageable(any(),any());
    }

    @Test
    void getAllBulkUsersPaginationQWithSortTest()
    {
        BulkUserDefinition bulkUserDefinition=new BulkUserDefinition();
        bulkUserDefinition.setId(BigInteger.valueOf(101));
        Page<BulkUserDefinition> bulkUserDefinitionList=new PageImpl<>(List.of(bulkUserDefinition));
        Mockito.when(bulkUploadDefinintionRepository.findBulkUsersByQSortPageable(any(),any(),any(),any())).thenReturn(bulkUserDefinitionList);
        bulkUserServiceImplementation.getAllBulkUsers("101","createdOn","desc",PageRequest.of(1,1));
        Mockito.verify(bulkUploadDefinintionRepository,times(1)).findBulkUsersByQSortPageable(any(),any(),any(),any());
    }

    @Test
    void convertBulkEntityTest()
    {
        BulkUserDefinition bulkUserDefinitionTest=new BulkUserDefinition();
        bulkUserDefinitionTest.setId(BigInteger.valueOf(101));
        Mockito.when(objectMapper.convertValue(any(), (Class<Object>) any())).thenReturn(new BulkUserResponse("101",null,"101","created"));
        Assertions.assertNotNull(bulkUserServiceImplementation.convertBulkEntityToDTO(bulkUserDefinitionTest));
    }
    @Test
    void checkValidFileNameTest()
    {
       Assertions.assertThrows(InvalidDataException.class,()->bulkUserServiceImplementation.checkValidFileName("file"));
    }

    @Test
    void checkValidFileNameMoreDotsTest()
    {
        Assertions.assertThrows(InvalidDataException.class,()->bulkUserServiceImplementation.checkValidFileName("file.more.than.one.dot.in.file.name"));
    }

    @Test
    void checkValidFileNameSuccessTest()
    {
        Assertions.assertDoesNotThrow(()->bulkUserServiceImplementation.checkValidFileName("accounts.csv"));
    }

    @Test
    void getAllBulkUsersFilterTest()
    {
        Assertions.assertNotNull(bulkUserServiceImplementation.getAllBulkUsers(DOCUMENT_ID,"101",CREATED_ON,"asc"));
    }

    @Test
    void getAllBulkUsersPagination()
    {
        Assertions.assertNotNull(bulkUserServiceImplementation.getAllBulkUsers(DOCUMENT_ID,"123",CREATED_ON,"asc"));
    }

    @Test
    void deleteBulkUserById()
    {
        Mockito.when(bulkUploadDefinintionRepository.existsById(BigInteger.valueOf(1))).thenReturn(true).thenReturn(false);
        Mockito.when(bulkUploadDefinintionRepository.deleteById(BigInteger.valueOf(1))).thenReturn(1);
        bulkUserServiceImplementation.deleteBulkUserById("1");
        Assertions.assertThrows(BulkUserNotFoundException.class,()->bulkUserServiceImplementation.deleteBulkUserById("1"));
    }
    @Test
    void bulkUploadStatus() throws JsonProcessingException {
        BulkUploadSchema bulkUploadSchema = new BulkUploadSchema("1",map,"1","status");
        Mockito.when(userServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list);
        Mockito.when(bulkUploadDefinintionRepository.existsById(BigInteger.valueOf(1))).thenReturn(false);
        Assertions.assertThrows(BulkUserNotFoundException.class,()->bulkUserServiceImplementation.bulkUpdateStatus(bulkUploadSchema));
    }

}
