package com.sodep.miniSpi.config;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI miniSpiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini SPI API")
                        .description("""
                                API REST para el simulador de transferencias inmediatas Mini SPI.

                                El sistema permite realizar transferencias entre cuentas,
                                garantizando idempotencia, control de fondos y consistencia
                                transaccional.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Mini SPI")));
    }
}
