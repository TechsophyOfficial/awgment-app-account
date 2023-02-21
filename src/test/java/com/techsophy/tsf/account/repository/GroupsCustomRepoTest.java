package com.techsophy.tsf.account.repository;

import com.techsophy.tsf.account.entity.GroupDefinition;
import com.techsophy.tsf.account.repository.document.GroupsCustomRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class GroupsCustomRepoTest
{
    @Mock
    MongoTemplate mockMongoTemplate;
    @Mock
    GroupDefinition mockGroupDefinition;
    @InjectMocks
    GroupsCustomRepositoryImpl mockGroupsCustomRepoImpl;

    @Test
    void findByIdTest()
    {
        Mockito.when(mockMongoTemplate.find(Mockito.any(),Mockito.any())).thenReturn(List.of());
        mockGroupsCustomRepoImpl.findByIdIn(List.of());
        Mockito.verify(mockMongoTemplate,Mockito.times(1)).find(Mockito.any(),Mockito.any());
    }

    @Test
    void findGroupsByQPageableTest()
    {
        Mockito.when(mockMongoTemplate.find(Mockito.any(),Mockito.any())).thenReturn(List.of());
        mockGroupsCustomRepoImpl.findGroupsByQPageable("", PageRequest.of(1,1));
        Mockito.verify(mockMongoTemplate,Mockito.times(1)).find(Mockito.any(),Mockito.any());
    }

    @Test
    void findGroupsByQSortingTest()
    {
        Mockito.when(mockMongoTemplate.find(Mockito.any(),Mockito.any())).thenReturn(List.of());
        mockGroupsCustomRepoImpl.findGroupsByQSorting("", Sort.by("acd"));
        Mockito.verify(mockMongoTemplate,Mockito.times(1)).find(Mockito.any(),Mockito.any());
    }
}
