package com.techsophy.tsf.account.config;

import com.techsophy.idgenerator.IdGeneratorImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Bean
    public IdGeneratorImpl idGenerator(@Value("${awgment.id-generator.node-id:-1}") long nodeId) {
        return nodeId >= 0 ? new IdGeneratorImpl(nodeId) : new IdGeneratorImpl();
    }
}
