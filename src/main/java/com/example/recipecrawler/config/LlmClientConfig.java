package com.example.recipecrawler.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The outbound {@link RestClient} for the optional free-text LLM fallback
 * ({@link com.example.recipecrawler.parse.LlmRecipeParser}). It gets its own
 * client because an LLM call routinely takes far longer than the shared
 * {@code spring.http.clients.*} read timeout (10s). Circuit-breaker name
 * {@code llm}; the bearer token is added per-request by the client, not here.
 */
@Configuration
public class LlmClientConfig {

    @Bean
    RestClient llmClient(RestClient.Builder builder,
                         CircuitBreakerRegistry breakers,
                         @Value("${llm.read-timeout-seconds:45}") long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return builder
                .requestFactory(factory)
                .requestInterceptor(DownstreamResilience.forTarget(breakers, "llm"))
                .build();
    }
}
