package com.techsophy.tsf.account.config;

import com.techsophy.idgenerator.IdGeneratorImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Bean
    public IdGeneratorImpl idGenerator() {
        return new IdGeneratorImpl();
    }
}
