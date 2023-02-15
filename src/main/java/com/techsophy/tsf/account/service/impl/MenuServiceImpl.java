package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.MenuResponseSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.entity.MenuDefinition;
import com.techsophy.tsf.account.exception.NoDataFoundException;
import com.techsophy.tsf.account.repository.MenuRepository;
import com.techsophy.tsf.account.service.MenuService;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.MENU_NOT_FOUND_EXCEPTION;

@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class MenuServiceImpl implements MenuService
{
    private final MenuRepository menuRepository;
    private final ObjectMapper objectMapper;
    private final IdGeneratorImpl idGenerator;
    private final GlobalMessageSource globalMessageSource;
    private final UserDetails userDetails;

    @Override
    public MenuResponseSchema saveMenu(MenuSchema menuSchema) throws JsonProcessingException
    {
        MenuDefinition menuDefinition = objectMapper.convertValue(menuSchema, MenuDefinition.class);
        Map<String,Object> loggedInUser = userDetails.getUserDetails().get(0);
        String menuId = menuSchema.getId();
        if(menuId==null)
        {
            menuDefinition.setId(idGenerator.nextId());
            menuDefinition.setVersion(1);
            menuDefinition.setCreatedOn(Instant.now());
            menuDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        }
        else
        {
            MenuDefinition menuDefinitionData =this.menuRepository.findById(Long.valueOf(menuId))
                    .orElseThrow(() -> new NoDataFoundException(MENU_NOT_FOUND_EXCEPTION,globalMessageSource.get(MENU_NOT_FOUND_EXCEPTION,menuId)));
            menuDefinition.setId(menuDefinition.getId());
            menuDefinition.setCreatedOn(menuDefinitionData.getCreatedOn());
            menuDefinition.setCreatedById(menuDefinitionData.getCreatedById());
            menuDefinition.setVersion(menuDefinitionData.getVersion() + 1);
        }
        menuDefinition.setUpdatedOn(Instant.now());
        menuDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        MenuDefinition menuDefinitionResponse = this.menuRepository.save(menuDefinition);
        MenuResponseSchema responseDto=new MenuResponseSchema();
        responseDto.setId(menuDefinitionResponse.getId().toString());
        return responseDto;
    }

    @Override
    public MenuSchema getMenuById(String id)
    {
        MenuDefinition menuDefinition = menuRepository.findById((BigInteger.valueOf(Long.parseLong(id)))).orElseThrow(() -> new NoDataFoundException(MENU_NOT_FOUND_EXCEPTION,globalMessageSource.get(MENU_NOT_FOUND_EXCEPTION, id)));
        return this.objectMapper.convertValue(menuDefinition,MenuSchema.class);
    }

    @Override
    public Stream<MenuSchema> getAllMenus()
    {
        return this.menuRepository.findAll().stream()
                .map(menuDefinition -> this.objectMapper.convertValue(menuDefinition, MenuSchema.class));
    }

    @Override
    public void deleteMenuById(String id)
    {
        if(menuRepository.existsById(BigInteger.valueOf(Long.parseLong(id))))
        {
            this.menuRepository.deleteById(BigInteger.valueOf(Long.parseLong(id)));
        }
        else
        {
            throw new NoDataFoundException(MENU_NOT_FOUND_EXCEPTION,globalMessageSource.get(MENU_NOT_FOUND_EXCEPTION,id));
        }
    }
}
