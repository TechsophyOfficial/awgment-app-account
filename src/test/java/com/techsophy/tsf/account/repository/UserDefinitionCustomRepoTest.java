package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.entity.UserDefinition;
import com.techsophy.tsf.account.repository.document.UserDefinitionCustomRepositoryImpl;
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
import java.util.List;
import static com.techsophy.tsf.account.constants.UserFormDataConstants.ANYSTRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDefinitionCustomRepoTest
{
    @Mock
    MongoTemplate mongoTemplate;
    @InjectMocks
    UserDefinitionCustomRepositoryImpl userDefinitionCustomRepository;

    @Test
    void findUserByQSort()
    {
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING);
        Mockito.when(mongoTemplate.find(any(),eq(UserDefinition.class))).thenReturn(List.of(userDefinition));
        userDefinitionCustomRepository.findUserByQSort("abc", Sort.by("abc"));
        verify(mongoTemplate,times(1)).find(any(),eq(UserDefinition.class));
    }

    @Test
    void findUserByQPageable()
    {
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING);
        Mockito.when(mongoTemplate.find(any(),eq(UserDefinition.class))).thenReturn(List.of(userDefinition));
        Pageable page =  PageRequest.of(1,1);
        userDefinitionCustomRepository.findUserByQPageable("abc", page);
        verify(mongoTemplate,times(1)).find(any(),eq(UserDefinition.class));
    }

    @Test
    void findAllUsers()
    {
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING);
        Mockito.when(mongoTemplate.find(any(),eq(UserDefinition.class))).thenReturn(List.of(userDefinition));
        userDefinitionCustomRepository.findAllUsers(Sort.by("abc"));
        verify(mongoTemplate,times(1)).find(any(),eq(UserDefinition.class));
    }

    @Test
    void findAllUsersTest()
    {
        Pageable page =  PageRequest.of(1,1);
        UserDefinition userDefinition = new UserDefinition(BigInteger.ONE,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING,ANYSTRING);
        Mockito.when(mongoTemplate.find(any(),eq(UserDefinition.class))).thenReturn(List.of(userDefinition));
        userDefinitionCustomRepository.findAllUsers(page);
        verify(mongoTemplate,times(1)).find(any(),eq(UserDefinition.class));
    }
}
