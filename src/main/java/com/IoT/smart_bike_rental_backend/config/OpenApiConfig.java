package com.IoT.smart_bike_rental_backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Bike Rental System")
                        .version("1.0.0")
                        .description("A comprehensive IoT-based bike rental system with real-time management, MQTT integration, and JWT authentication.")
                        .contact(new Contact()
                                .name("Smart Bike Team")
                                .email("info@smartbike.com")
                                .url("https://smartbike.com")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Authentication Token")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
