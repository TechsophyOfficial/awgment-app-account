package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.BulkUserControllerImpl;
import com.techsophy.tsf.account.dto.BulkUploadResponse;
import com.techsophy.tsf.account.dto.BulkUploadSchema;
import com.techsophy.tsf.account.exception.InvalidInputException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.BulkUserService;
import com.techsophy.tsf.account.utils.TokenUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.util.HashMap;
import java.util.Map;
import static com.techsophy.tsf.account.constants.ThemesConstants.CREATEDON;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
class BulkUserControllerTest
{
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    TokenUtils tokenUtils;
    @Mock
    BulkUserService bulkUserService;
    @InjectMocks
    BulkUserControllerImpl bulkUserController;
    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void bulkUploadUsers() throws Exception
    {
        MockMultipartFile multipartFile = new MockMultipartFile("file", "test.txt",
                                   "text/plain", "Spring Framework".getBytes());
        ApiResponse<BulkUploadResponse> response = bulkUserController.bulkUploadUsers(multipartFile);
        Assertions.assertNotNull(response);
    }

    @Test
    void bulkUpdateStatus() throws Exception
    {
        Map<String,Object> map = new HashMap<>();
        map.put("abc","abc");
        BulkUploadSchema bulkUploadSchema = new BulkUploadSchema("1",map,"1","abc");
        ApiResponse<BulkUploadResponse> response = bulkUserController.bulkUpdateStatus(bulkUploadSchema);
        Assertions.assertNotNull(response);
    }

    @Test
    void deleteBulkUserById()
    {
        ApiResponse response = bulkUserController.deleteBulkUserById("1");
        Assertions.assertNotNull(response);
    }

    @Test
    void getAllBulkUsers()
    {
        ApiResponse response =bulkUserController.getAllBulkUsers("abc",0,1,CREATEDON,"asc" , "abc", "abc");
        ApiResponse response1 =bulkUserController.getAllBulkUsers("abc",0,1,CREATEDON,"asc" , null, "abc");
        ApiResponse response2 =bulkUserController.getAllBulkUsers("abc",1,1,CREATEDON, "asc", null, "abc");
        ApiResponse response3 = bulkUserController.getAllBulkUsers("abc",1,1,CREATEDON, "asc", "abc", "abc");
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response1);
        Assertions.assertNotNull(response2);
        Assertions.assertNotNull(response3);
    }

    @Test
    void getAllBulkUsersListWithFilterTest()
    {
        Assertions.assertNotNull(bulkUserController.getAllBulkUsers(null,null,null,CREATEDON,"asc" , "abc", "abc"));
    }

    @Test
    void getAllBulkUsersQTest()
    {
        Assertions.assertNotNull(bulkUserController.getAllBulkUsers("abc",null,null,CREATEDON,"asc" , "",""));
    }

    @Test
    void bulkUserInvalidInputExceptionTest()
    {
        Assertions.assertThrows(InvalidInputException.class,()->bulkUserController.getAllBulkUsers(null,null,null,null,null,null,null));
    }
}

