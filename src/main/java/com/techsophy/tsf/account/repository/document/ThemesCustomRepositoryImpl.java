package com.techsophy.tsf.account.repository.document;

import com.techsophy.tsf.account.entity.ThemesDefinition;
import com.techsophy.tsf.account.repository.ThemesCustomRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static com.techsophy.tsf.account.constants.AccountConstants.*;

@AllArgsConstructor
public class ThemesCustomRepositoryImpl implements ThemesCustomRepository
{
    private final MongoTemplate mongoTemplate;

    @Override
    public boolean existsByName(String name)
    {
        Query query=new Query(Criteria.where("name").is(name));
       return mongoTemplate.exists(query,ThemesDefinition.class);
    }

    @Override
    public List<ThemesDefinition> findByIdIn(List<String> idList)
    {
        Query query = new Query(Criteria.where(UNDERSCORE_ID).in(idList));
        return mongoTemplate.find(query, ThemesDefinition.class);
    }

    @Override
    public Stream<ThemesDefinition> findThemesByQSorting(String q, Sort sort)
    {
        Query query = new Query();
        String searchString = URLDecoder.decode(q, StandardCharsets.UTF_8);
        query.addCriteria(new Criteria().orOperator(Criteria.where(ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                Criteria.where(THEME_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                Criteria.where(THEME_CONTENT).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
        )).with(sort);
        query.with(Sort.by(Sort.Direction.ASC, THEME_NAME));
        return mongoTemplate.find(query, ThemesDefinition.class).stream();
    }

    @Override
    public Page<ThemesDefinition> findThemesByQPageable(String q, Pageable pageable)
    {
        Query query = new Query();
        Query countQuery = new Query();
        String searchString = URLDecoder.decode(q, StandardCharsets.UTF_8);
        query.addCriteria(new Criteria().orOperator(Criteria.where(ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                Criteria.where(THEME_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                Criteria.where(THEME_CONTENT).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
        )).with(pageable);
        List<ThemesDefinition> formDefinitions = mongoTemplate.find(query, ThemesDefinition.class);
        query.with(Sort.by(Sort.Direction.ASC, THEME_NAME));
        countQuery.addCriteria(new Criteria().orOperator(Criteria.where(ID).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                Criteria.where(THEME_NAME).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
                Criteria.where(THEME_CONTENT).regex(Pattern.compile(searchString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
        ));
        long count=mongoTemplate.count(countQuery,ThemesDefinition.class);
        return new PageImpl<>(formDefinitions, pageable,count );    }
}
