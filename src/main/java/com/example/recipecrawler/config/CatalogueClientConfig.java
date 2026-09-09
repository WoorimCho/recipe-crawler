package com.example.recipecrawler.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The two outbound {@link RestClient}s. Timeouts come from the shared
 * {@code spring.http.clients.*} settings; each gets a circuit-breaker
 * interceptor so a dead catalogue fails fast instead of hanging an import.
 */
@Configuration
public class CatalogueClientConfig {

    private final CircuitBreakerRegistry breakers;

    public CatalogueClientConfig(CircuitBreakerRegistry breakers) {
        this.breakers = breakers;
    }

    @Bean
    RestClient recipeCatalogueClient(RestClient.Builder builder,
                                     @Value("${recipe-catalogue.url}") String baseUrl) {
        return builder.baseUrl(baseUrl)
                .requestInterceptor(DownstreamResilience.forTarget(breakers, "recipe-catalogue"))
                .build();
    }

    @Bean
    RestClient ingredientCatalogueClient(RestClient.Builder builder,
                                         @Value("${ingredient-catalogue.url}") String baseUrl) {
        return builder.baseUrl(baseUrl)
                .requestInterceptor(DownstreamResilience.forTarget(breakers, "ingredient-catalogue"))
                .build();
    }

    /** No base URL — the target is whatever recipe page the caller passes. */
    @Bean
    RestClient sourcePageClient(RestClient.Builder builder) {
        return builder
                .requestInterceptor(DownstreamResilience.forTarget(breakers, "recipe-source"))
                .build();
    }
}
