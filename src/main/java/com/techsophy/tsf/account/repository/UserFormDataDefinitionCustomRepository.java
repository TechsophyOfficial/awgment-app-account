package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.io.UnsupportedEncodingException;
import java.util.List;

public interface UserFormDataDefinitionCustomRepository
{
    List<UserFormDataDefinition> findByNameOrId(String idOrNameLike) throws UnsupportedEncodingException;

   UserFormDataDefinition findByUserName(String userName);
    List<UserFormDataDefinition> findAll(Sort sort);

    Page<UserFormDataDefinition> findAll(Pageable pageable);

    List<UserFormDataDefinition> findFormDataUserByQSort(String q, Sort sort);

    Page<UserFormDataDefinition> findFormDataUserByQPageable(String q, Pageable pageable);

    List<UserFormDataDefinition> findByFilterColumnAndValue(Sort sort,String filterColumn, String filterValue);

    Page<UserFormDataDefinition> findByFilterColumnAndValue(String filterColumn, String filterValue, Pageable pageable,String q);
}
