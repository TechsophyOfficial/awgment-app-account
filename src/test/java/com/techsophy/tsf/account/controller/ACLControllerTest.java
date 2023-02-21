package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.ACLControllerImpl;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.CheckACLSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.impl.ACLServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;

import java.nio.file.AccessDeniedException;
import static com.techsophy.tsf.account.constants.AccountConstants.ID_VALUE;

@ExtendWith({MockitoExtension.class})
class ACLControllerTest
{
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    TokenUtils mockTokenUtils;
    @Mock
    ACLServiceImpl aclServiceImpl;
    @Mock
    ObjectMapper objectMapper;
    @InjectMocks
    ACLControllerImpl aclController;

    @Test
    void saveACLTest() throws Exception
    {
        ACLSchema aclSchema=new ACLSchema();
        Mockito.when(globalMessageSource.get("ACL.SAVED.SUCCESS")).thenReturn("ACL saved successfully");
        ApiResponse apiResponse=new ApiResponse(aclSchema,true,"ACL saved successfully");
        Mockito.when(aclServiceImpl.saveACL(aclSchema)).thenReturn(aclSchema);
        Assertions.assertEquals(apiResponse,aclController.saveACL(aclSchema));
    }

    @Test
    void saveACLDuplicateNameDBExceptionTest() throws Exception
    {
        ACLSchema aclSchema=new ACLSchema();
        Mockito.when(aclServiceImpl.saveACL(aclSchema)).thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate names are not allowed for ACL"));
        Assertions.assertThrows(DuplicateKeyException.class,()->aclController.saveACL(aclSchema));
    }

    @Test
    void geAllACLsDBFailedExceptionTest()
    {
        Mockito.when(aclServiceImpl.getAllACLs(PageRequest.of(0,5))).thenThrow(new MongoException("Mongo connection got expired"));
        Assertions.assertThrows(MongoException.class,()->aclController.getAllACLs(0,5 ));
    }

    @Test
    void geAllACLsPaginationTest()
    {
        Mockito.when(globalMessageSource.get("ACL.RETRIEVE.SUCCESS")).thenReturn("ACL retrieved successfully");
        ApiResponse apiResponse=new ApiResponse(null,true,"ACL retrieved successfully");
        Assertions.assertEquals(apiResponse,aclController.getAllACLs(0,5 ));
    }

    @Test
    void getACLByIdTest()
    {
        Mockito.when(globalMessageSource.get("ACL.RETRIEVE.SUCCESS")).thenReturn("ACL retrieved successfully");
        ApiResponse apiResponse=new ApiResponse(null,true,"ACL retrieved successfully");
        Assertions.assertEquals(apiResponse,aclController.getACLById(ID_VALUE));
    }

    @Test
    void geAclDBFailedExceptionTest()
    {
        Mockito.when(aclServiceImpl.getACLById(ID_VALUE)).thenThrow(new MongoException("Mongo connection got expired"));
        Assertions.assertThrows(MongoException.class,()->aclController.getACLById(ID_VALUE ));
    }

    @Test
    void checkAclAccessDBFailedTest() throws JsonProcessingException,AccessDeniedException
    {
        CheckACLSchema checkACLSchema=new CheckACLSchema();
        Mockito.when(aclServiceImpl.checkACLAccess(ID_VALUE,checkACLSchema)).thenThrow(new MongoException("Mongo connection got expired"));
        Assertions.assertThrows(MongoException.class,()->aclController.checkACLAccess(ID_VALUE,checkACLSchema));
    }

    @Test
    void checkAccessContextNullTest() throws JsonProcessingException, AccessDeniedException
    {
        Mockito.when(globalMessageSource.get("ACL.EVALUATE.SUCCESS")).thenReturn("ACL evaluated successfully");
        ApiResponse apiResponse=new ApiResponse(null,true,"ACL evaluated successfully");
        Assertions.assertEquals(apiResponse,aclController.checkACLAccess(ID_VALUE,null));
    }

    @Test
    void checkAccessContextTest() throws JsonProcessingException, AccessDeniedException
    {
        CheckACLSchema checkACLSchema=new CheckACLSchema();
        Mockito.when(globalMessageSource.get("ACL.EVALUATE.SUCCESS")).thenReturn("ACL evaluated successfully");
        ApiResponse apiResponse=new ApiResponse(null,true,"ACL evaluated successfully");
        Assertions.assertEquals(apiResponse,aclController.checkACLAccess(ID_VALUE,checkACLSchema));
    }
}
