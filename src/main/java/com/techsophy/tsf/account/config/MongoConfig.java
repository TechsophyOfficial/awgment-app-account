package com.techsophy.tsf.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Configuration class for MongoDB transactions.
 */
@Configuration
public class MongoConfig {

    /**
     * Configures and provides a MongoDB transaction manager.
     *
     * @param dbFactory MongoDatabaseFactory instance
     * @return MongoTransactionManager
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
