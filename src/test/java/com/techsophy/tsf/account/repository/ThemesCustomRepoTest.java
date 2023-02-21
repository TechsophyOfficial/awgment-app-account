package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.repository.document.ThemesCustomRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import static com.techsophy.tsf.account.constants.UserFormDataConstants.ANYSTRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThemesCustomRepoTest
{
    @Mock
    MongoTemplate mongoTemplate;
    @InjectMocks
    ThemesCustomRepositoryImpl themesCustomRepository;

    @Test
    void findByIdIn()
    {
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING);
        List<String> list = new ArrayList<>();
        list.add("abc");
        Mockito.when(mongoTemplate.find(any(),eq(ThemesDefinition.class))).thenReturn(List.of(themesDefinition));
        themesCustomRepository.findByIdIn(list);
        verify(mongoTemplate,times(1)).find(any(),eq(ThemesDefinition.class));
    }

    @Test
    void findThemesByQSorting()
    {
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING);
        Mockito.when(mongoTemplate.find(any(),eq(ThemesDefinition.class))).thenReturn(List.of(themesDefinition));
        themesCustomRepository.findThemesByQSorting("abc", Sort.by("abc"));
        verify(mongoTemplate,times(1)).find(any(),eq(ThemesDefinition.class));
    }

    @Test
    void findThemesByQPageable()
    {
        ThemesDefinition themesDefinition = new ThemesDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING);
        Mockito.when(mongoTemplate.find(any(),eq(ThemesDefinition.class))).thenReturn(List.of(themesDefinition));
        Pageable page =  PageRequest.of(1,1);
        themesCustomRepository.findThemesByQPageable("abc", page);
        verify(mongoTemplate,times(1)).find(any(),eq(ThemesDefinition.class));
    }
}
