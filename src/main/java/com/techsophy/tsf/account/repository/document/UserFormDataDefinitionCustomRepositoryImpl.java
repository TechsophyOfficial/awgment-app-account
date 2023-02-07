package com.techsophy.tsf.account.repository.document;

import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.repository.UserFormDataDefinitionCustomRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@AllArgsConstructor
public class UserFormDataDefinitionCustomRepositoryImpl implements UserFormDataDefinitionCustomRepository
{
    private final MongoTemplate mongoTemplate;
    
    @Override
    public List<UserFormDataDefinition> findByNameOrId(String idOrNameLike)
    {
        Query query = new Query();
        String searchString = URLDecoder.decode(idOrNameLike, StandardCharsets.UTF_8);
        query.addCriteria(new Criteria().orOperator(where(USER_DEFINITION_ID).regex(searchString), where(USER_DEFINITION_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))));
       return mongoTemplate.find(query, UserFormDataDefinition.class);
    }

    @Override
    public List<UserFormDataDefinition> findAll(Sort sort)
    {
        Query query = new Query();
        query.addCriteria(where(USER_DATA_USER_NAME).ne(SYSTEM)).with(sort);
        return mongoTemplate.find(query, UserFormDataDefinition.class);
    }

    @Override
    public Page<UserFormDataDefinition> findAll(Pageable pageable)
    {
        Query query = new Query();
        Query countQuery = new Query();
        query.addCriteria(where(USER_DATA_USER_NAME).ne(SYSTEM)).with(pageable);
        List<UserFormDataDefinition> userFormDataDefinition= mongoTemplate.find(query,UserFormDataDefinition.class);
        countQuery.addCriteria(where(USER_DATA_USER_NAME).ne(SYSTEM));
        long count=mongoTemplate.count(countQuery,UserFormDataDefinition.class);
        return new PageImpl<>(userFormDataDefinition, pageable, count - 1);
    }

    @Override
    public List<UserFormDataDefinition> findFormDataUserByQSort(String q, Sort sort)
    {
        Query query = new Query();
        String searchString = URLDecoder.decode(q, StandardCharsets.UTF_8);
        query.addCriteria(new Criteria().orOperator(where(ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_FIRST_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_LAST_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_MOBILE_NUMBER).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_EMAIL_ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_DEPARTMENT).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_USER_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
        )).with(sort);
        query.addCriteria(where(USER_DATA_USER_NAME).ne(SYSTEM));
        query.with(Sort.by(Sort.Direction.ASC, USER_DATA_USER_NAME));
        return mongoTemplate.find(query, UserFormDataDefinition.class);
    }

    @Override
    public Page<UserFormDataDefinition> findFormDataUserByQPageable(String q, Pageable pageable)
    {
        Query query = new Query();
        Query countQuery = new Query();
        String searchString = URLDecoder.decode(q, StandardCharsets.UTF_8);
        query.addCriteria(new Criteria().orOperator(where(ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_FIRST_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_LAST_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_MOBILE_NUMBER).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_EMAIL_ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_DEPARTMENT).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_USER_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
        )).addCriteria(where(USER_DATA_USER_NAME).ne(SYSTEM)).with(pageable);
        List<UserFormDataDefinition> userFormDataDefinitions = mongoTemplate.find(query, UserFormDataDefinition.class);
        query.with(Sort.by(Sort.Direction.ASC, USER_DEFINITION_USER_NAME));
        countQuery.addCriteria(new Criteria().orOperator(where(ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_FIRST_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_LAST_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_MOBILE_NUMBER).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_EMAIL_ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_DEPARTMENT).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_DATA_USER_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                where(USER_ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
        ));
        countQuery.addCriteria(where(USER_DATA_USER_NAME).ne(SYSTEM));
        long count=mongoTemplate.count(countQuery,UserFormDataDefinition.class);
        return new PageImpl<>(userFormDataDefinitions, pageable,count );
    }

    @Override
    public List<UserFormDataDefinition> findByFilterColumnAndValue(Sort sort, String filterColumn, String filterValue)
    {
        filterValue=Character.isWhitespace(filterValue.charAt(0))&&filterValue.trim().matches("\\d")?"+"+filterValue.trim():filterValue;
        Query query=new Query();
        Collation collation = Collation.of(COLLATION_EN).strength(1);
        query.addCriteria(new Criteria().andOperator(where(USER_DATA+DOT+filterColumn).is(filterValue)
                ,Criteria.where(USER_DATA_USER_NAME).ne(SYSTEM))).with(sort).collation(collation);
        query.with(Sort.by(Sort.Direction.ASC, USER_DATA_USER_NAME));
        return mongoTemplate.find(query,UserFormDataDefinition.class);
    }

    @Override
    public Page<UserFormDataDefinition> findByFilterColumnAndValue(String filterColumn, String filterValue, Pageable pageable,String q)
    {
        Query query = new Query();
        Query countQuery = new Query();
        Collation collation = Collation.of(COLLATION_EN).strength(1);
        query.addCriteria(new Criteria().andOperator(where(USER_DATA+DOT+filterColumn).is(filterValue)
                ,Criteria.where(USER_DATA_USER_NAME).ne(SYSTEM))).with(pageable).collation(collation);
        List<UserFormDataDefinition> userFormDataDefinitions = mongoTemplate.find(query, UserFormDataDefinition.class);

        query.with(Sort.by(Sort.Direction.ASC, USER_DEFINITION_USER_NAME));
        countQuery.addCriteria(new Criteria().andOperator(where(USER_DATA+DOT+filterColumn).is(filterValue)
                ,Criteria.where(USER_DATA_USER_NAME).ne(SYSTEM)));
        long count=mongoTemplate.count(countQuery,UserFormDataDefinition.class);
        return new PageImpl<>(userFormDataDefinitions, pageable,count );


    }
}
