package com.techsophy.tsf.account;

import io.mongock.runner.springboot.EnableMongock;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import static com.techsophy.tsf.account.constants.AccountConstants.CURRENT_PROJECT;
import static com.techsophy.tsf.account.constants.AccountConstants.MULTITENANCY_PROJECT;

@RefreshScope
@EnableMongoRepositories
@EnableMongoAuditing
@SpringBootApplication
@ComponentScan({CURRENT_PROJECT,MULTITENANCY_PROJECT})
@OpenAPIDefinition
@EnableMongock
public class TechsophyPlatformAccountApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(TechsophyPlatformAccountApplication.class, args);
    }
}
