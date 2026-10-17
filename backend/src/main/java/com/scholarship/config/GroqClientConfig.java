package com.scholarship.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.net.URI;

/**
 * Ensures compatibility between Spring AI OpenAI starter and Groq API endpoints.
 * Automatically handles baseUrl variations (e.g. /openai/v1 vs /openai) to prevent duplicate /v1 paths.
 */
@Configuration
public class GroqClientConfig {

    @Bean
    public RestClientCustomizer groqUrlNormalizingCustomizer() {
        return restClientBuilder -> restClientBuilder.requestInterceptor((request, body, execution) -> {
            URI uri = request.getURI();
            String uriStr = uri.toString();
            if (uriStr.contains("/v1/v1/")) {
                URI newUri = URI.create(uriStr.replace("/v1/v1/", "/v1/"));
                HttpRequest wrapper = new HttpRequestWrapper(request) {
                    @Override
                    public URI getURI() {
                        return newUri;
                    }
                };
                return execution.execute(wrapper, body);
            }
            return execution.execute(request, body);
        });
    }
}
