package com.scholarship.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI scholarshipOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered Scholarship Recommendation System API")
                        .description("Production-grade Java Spring Boot backend for managing scholarships, student profiles, evaluating deterministic eligibility criteria, and orchestrating RAG recommendations with FastAPI and Gemini.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Scholarship Advisory Engineering Team")
                                .url("https://scholarships.gov.in/"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }
}
