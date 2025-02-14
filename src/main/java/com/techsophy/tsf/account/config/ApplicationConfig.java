package com.techsophy.tsf.account.config;

import com.techsophy.idgenerator.IdGeneratorImpl;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Configuration class for application-wide beans.
 * This class configures beans for ID generation and OpenAPI documentation.
 */
@Configuration
public class ApplicationConfig {

    @Value(GATEWAY_URL)
    String gatewayUrl;

    /**
     * Bean definition for IdGeneratorImpl.
     * This provides a globally available ID generator instance.
     *
     * @return a new instance of IdGeneratorImpl.
     */
    @Bean
    public IdGeneratorImpl idGeneratorImpl() {
        return new IdGeneratorImpl();
    }

    /**
     * Bean definition for OpenAPI configuration.
     * Configures API documentation with a title, version, and server URL.
     *
     * @return an OpenAPI instance with predefined settings.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title(ACCOUNT_MODELER)
                        .version(VERSION_1)
                        .description(ACCOUNT_MODELER_API_VERSION_1))
                .servers(List.of(new Server().url(gatewayUrl)));
    }
}
