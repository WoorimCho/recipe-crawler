package com.example.recipecrawler.config;

import com.example.recipecrawler.internal.InternalAuthClientInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The three outbound {@link RestClient}s. Timeouts come from the shared
 * {@code spring.http.clients.*} settings; each gets a circuit-breaker
 * interceptor so a dead target fails fast. The two catalogue clients also sign
 * every request with an HMAC {@code X-Internal-Auth} header (SECURITY.md C2).
 */
@Configuration
public class CatalogueClientConfig {

    private final CircuitBreakerRegistry breakers;
    private final InternalAuthClientInterceptor internalAuth;

    public CatalogueClientConfig(CircuitBreakerRegistry breakers,
                                 @Value("${internal-auth.secret:dev-internal-secret-change-me}") String internalAuthSecret) {
        this.breakers = breakers;
        this.internalAuth = new InternalAuthClientInterceptor(internalAuthSecret);
    }

    @Bean
    RestClient recipeCatalogueClient(RestClient.Builder builder,
                                     @Value("${recipe-catalogue.url}") String baseUrl) {
        return builder.baseUrl(baseUrl)
                .requestInterceptor(internalAuth)
                .requestInterceptor(DownstreamResilience.forTarget(breakers, "recipe-catalogue"))
                .build();
    }

    @Bean
    RestClient ingredientCatalogueClient(RestClient.Builder builder,
                                         @Value("${ingredient-catalogue.url}") String baseUrl) {
        return builder.baseUrl(baseUrl)
                .requestInterceptor(internalAuth)
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
