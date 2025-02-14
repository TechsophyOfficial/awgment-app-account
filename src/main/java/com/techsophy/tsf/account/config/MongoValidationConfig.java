package com.techsophy.tsf.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.mapping.event.ValidatingMongoEventListener;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Configuration class for enabling validation in MongoDB entities.
 */
@Configuration
public class MongoValidationConfig {

    /**
     * Creates a ValidatingMongoEventListener bean to enable validation
     * on MongoDB entities before saving them.
     *
     * @param factory LocalValidatorFactoryBean for validation
     * @return ValidatingMongoEventListener instance
     */
    @Bean
    @Primary
    public ValidatingMongoEventListener validatingMongoEventListener(LocalValidatorFactoryBean factory) {
        return new ValidatingMongoEventListener(factory);
    }

    /**
     * Creates a LocalValidatorFactoryBean to provide validation capabilities.
     *
     * @return LocalValidatorFactoryBean instance
     */
    @Bean
    @Primary
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }
}
