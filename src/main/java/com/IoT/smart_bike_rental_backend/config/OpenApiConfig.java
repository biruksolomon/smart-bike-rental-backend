package com.IoT.smart_bike_rental_backend.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.version}")
    private String appVersion;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${spring.application.description}")
    private String appDescription;

    @Value("${spring.application.base-url}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        List<Server> servers = new ArrayList<>();

        log.info("init swagger");
        servers.add(new Server()
                .url(baseUrl )
                .description("Local Development Server"));
        servers.add(new Server()
                .url("http://16.171.65.18")
                .description("Cloud Dev server")
        );
        servers.add(new Server()
                .url("https://016c-196-188-188-143.ngrok-free.app")
                .description("Ngrok Dev Server")

        );



     /*   servers.add(new Server()
                .url("https://api.skegmarket.com")
                .description("Production Server"));

        servers.add(new Server()
                .url("https://staging-api.skegmarket.com")
                .description("Staging Server"));*/

        return new OpenAPI()
                .info(new Info()
                        .title(appName + " API")
                        .version(appVersion)
                        .description(appDescription)
                        .contact(new Contact()
                                .name(appName + " Development Team")
                                .email("besot@tech.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(servers)
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createBearerAuthScheme()));
    }

    private SecurityScheme createBearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer")
                .name("Authorization")
                .description("Enter your JWT token");
    }
}
