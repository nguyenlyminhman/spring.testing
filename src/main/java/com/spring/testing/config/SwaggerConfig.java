package com.spring.testing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SwaggerConfig {
    @Value("${server.port}")
    private String serverPort;
    @Bean
    public OpenAPI customOpenAPI() {
        // Define servers
        Server localServer = new Server()
                .url("http://localhost:" + serverPort)
                .description("Local Server");
        Server devServer = new Server()
                .url("https://dev.api.your-dev-domain.com")
                .description("Development Server");
        Server prodServer = new Server()
                .url("https://api.your-prod-domain.com")
                .description("Production Server");
        // Define security scheme (JWT Bearer token or access-token)
        SecurityScheme bearerAuthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT") // Or custom format if needed
                .in(SecurityScheme.In.HEADER)
                .name("Authorization"); // If you use access-token, change this to "access-token"
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Testing API")
                        .description("API documentation for the Testing system")
                        .version("1.0.0"))
                .servers(Arrays.asList(localServer, devServer, prodServer))
         .components(new Components().addSecuritySchemes("bearerAuth", bearerAuthScheme))
         .addSecurityItem(securityRequirement); // Apply to all APIs by default
    }
}
