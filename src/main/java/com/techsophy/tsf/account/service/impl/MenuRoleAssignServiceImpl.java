package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.MenuRoleAssignResponseSchema;
import com.techsophy.tsf.account.dto.MenuRoleAssignSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.entity.MenuRoleAssignDefinition;
import com.techsophy.tsf.account.exception.NoDataFoundException;
import com.techsophy.tsf.account.repository.MenuRoleAssignRepository;
import com.techsophy.tsf.account.service.MenuRoleAssignService;
import com.techsophy.tsf.account.service.MenuService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.MENU_NOT_FOUND_EXCEPTION;

@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class MenuRoleAssignServiceImpl implements MenuRoleAssignService
{
    private final MenuRoleAssignRepository menuRoleAssignRepository;
    private final MenuService menuService;
    private final ObjectMapper objectMapper;
    private final IdGeneratorImpl idGenerator;
    private final GlobalMessageSource globalMessageSource;
    private final UserDetails userDetails;
    private final TokenUtils tokenUtils;

    @Override
    public MenuRoleAssignResponseSchema saveMenuRole(MenuRoleAssignSchema menuRoleAssignSchema) throws JsonProcessingException
    {
        MenuRoleAssignDefinition menuRoleAssignDefinition = objectMapper.convertValue(menuRoleAssignSchema, MenuRoleAssignDefinition.class);
        Map<String,Object> loggedInUser = userDetails.getUserDetails().get(0);
        String menuId = menuRoleAssignSchema.getId();
        if(menuId==null)
        {
            menuRoleAssignDefinition.setId(idGenerator.nextId());
            menuRoleAssignDefinition.setVersion(1);
            menuRoleAssignDefinition.setCreatedOn(Instant.now());
            menuRoleAssignDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        }
        else
        {
            MenuRoleAssignDefinition menuRoleAssignDefinitionData =
                    this.menuRoleAssignRepository.findById(Long.valueOf(menuId))
                            .orElseThrow(() -> new NoDataFoundException(MENU_NOT_FOUND_EXCEPTION,globalMessageSource.get(MENU_NOT_FOUND_EXCEPTION,menuId)));
            menuRoleAssignDefinition.setId(menuRoleAssignDefinitionData.getId());
            menuRoleAssignDefinition.setCreatedOn(menuRoleAssignDefinitionData.getCreatedOn());
            menuRoleAssignDefinition.setCreatedById(menuRoleAssignDefinitionData.getCreatedById());
            menuRoleAssignDefinition.setVersion(menuRoleAssignDefinitionData.getVersion() + 1);
        }
        menuRoleAssignDefinition.setUpdatedOn(Instant.now());
        menuRoleAssignDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        MenuRoleAssignDefinition menuDefinitionResponse = this.menuRoleAssignRepository.save(menuRoleAssignDefinition);
        MenuRoleAssignResponseSchema responseDto=new MenuRoleAssignResponseSchema();
        responseDto.setId(menuDefinitionResponse.getId().toString());
        return responseDto;
    }

    @Override
    public MenuRoleAssignSchema getMenuRole(String id)
    {
        MenuRoleAssignDefinition menuRoleAssignDefinition = menuRoleAssignRepository.findById((BigInteger.valueOf(Long.parseLong(id)))).orElseThrow(() -> new NoDataFoundException(MENU_NOT_FOUND_EXCEPTION,globalMessageSource.get(MENU_NOT_FOUND_EXCEPTION,id)));
        return this.objectMapper.convertValue(menuRoleAssignDefinition, MenuRoleAssignSchema.class);
    }

    @Override
    public Stream<MenuRoleAssignSchema> getAllMenuRole()
    {
        return this.menuRoleAssignRepository.findAll().stream()
                .map(menuRoleDefinition ->
                        this.objectMapper.convertValue(menuRoleDefinition, MenuRoleAssignSchema.class));
    }

    @Override
    public List<MenuSchema> getAssignedMenuToUserRoles()
    {
        List<MenuSchema> menuSchemas = new ArrayList<>();
        List<String> roles=tokenUtils.getClientRoles(tokenUtils.getTokenFromContext());
        roles.forEach(role ->
        {
            MenuRoleAssignDefinition menuRoleAssignDefinition = menuRoleAssignRepository.findByRole(role);
            if(menuRoleAssignDefinition !=null && StringUtils.isNotEmpty(menuRoleAssignDefinition.toString()))
            {
                List<String> menus = menuRoleAssignDefinition.getMenus();
                menus.forEach(menu ->
                {
                    MenuSchema menuSchema=menuService.getMenuById(menu);
                    menuSchemas.add(menuSchema);
                });
            }

        });
        return menuSchemas.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public void deleteMenuRoleById(String id)
    {
        if(menuRoleAssignRepository.existsById(BigInteger.valueOf(Long.parseLong(id))))
        {
            this.menuRoleAssignRepository.deleteById(BigInteger.valueOf(Long.parseLong(id)));
        }
        else
        {
            throw new NoDataFoundException(MENU_NOT_FOUND_EXCEPTION,globalMessageSource.get(MENU_NOT_FOUND_EXCEPTION,id));
        }
    }
}
