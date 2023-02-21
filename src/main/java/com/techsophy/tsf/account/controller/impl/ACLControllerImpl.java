package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.ACLController;
import com.techsophy.tsf.account.dto.ACLSchema;
import com.techsophy.tsf.account.dto.ACLValidate;
import com.techsophy.tsf.account.dto.CheckACLSchema;
import com.techsophy.tsf.account.entity.ACLDefinition;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.ACLService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.AccessDeniedException;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RestController
@AllArgsConstructor(onConstructor_ ={@Autowired})
public class ACLControllerImpl implements ACLController
{
    private final GlobalMessageSource globalMessageSource;
    private final ACLService aclService;

    @Override
    public ApiResponse<ACLSchema> saveACL(ACLSchema aclSchema) throws JsonProcessingException
    {
       return new ApiResponse<>(aclService.saveACL(aclSchema),true,globalMessageSource.get(ACL_SAVED_SUCCESSFULLY));
    }

    @Override
    public ApiResponse<Page<ACLDefinition>> getAllACLs(Integer page, Integer pageSize)
    {
        return new ApiResponse<>(aclService.getAllACLs(PageRequest.of(page,pageSize)),true,globalMessageSource.get(ACL_RETRIEVE_SUCCESS));
    }

    @Override
    public ApiResponse<ACLSchema> getACLById(String id)
    {
        return new ApiResponse<>(aclService.getACLById(id),true,globalMessageSource.get(ACL_RETRIEVE_SUCCESS));
    }

    @Override
    public ApiResponse<ACLValidate> checkACLAccess(String id, CheckACLSchema checkACLSchema) throws JsonProcessingException, AccessDeniedException
    {
        return new ApiResponse<>(aclService.checkACLAccess(id,checkACLSchema),true,globalMessageSource.get(ACL_EVALUATION_SUCCESS));
    }
}
