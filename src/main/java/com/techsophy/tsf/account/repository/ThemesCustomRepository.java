package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.entity.ThemesDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Stream;

@Repository
public interface ThemesCustomRepository
{
    boolean existsByName(String name);
    List<ThemesDefinition> findByIdIn(List<String> idList);
    Stream<ThemesDefinition> findThemesByQSorting(String q, Sort sort);
    Page<ThemesDefinition> findThemesByQPageable(String q, Pageable pageable);

}
