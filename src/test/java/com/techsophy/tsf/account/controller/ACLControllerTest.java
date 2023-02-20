package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.ACLControllerImpl;
import com.techsophy.tsf.account.dto.ACLSchema;
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
        ApiResponse apiResponse=new ApiResponse(null,true,"ACL saved successfully");
        Assertions.assertEquals(apiResponse,aclController.saveACL(aclSchema));
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
    void checkAccessTest() throws JsonProcessingException, AccessDeniedException
    {
        Mockito.when(globalMessageSource.get("ACL.EVALUATE.SUCCESS")).thenReturn("ACL evaluated successfully");
        ApiResponse apiResponse=new ApiResponse(null,true,"ACL evaluated successfully");
        Assertions.assertEquals(apiResponse,aclController.checkACLAccess(ID_VALUE));
    }
}
