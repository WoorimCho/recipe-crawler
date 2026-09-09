package com.example.recipecrawler.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.time.Duration;

/**
 * One {@link io.github.resilience4j.circuitbreaker.CircuitBreaker} per outbound
 * target (breaker names: {@code recipe-catalogue}, {@code ingredient-catalogue},
 * {@code recipe-source}), all sharing this config: trips when 50%+ of the last
 * 20 calls fail or run slow (&gt; 3s); OPEN 15s, then 4 probe calls. State is on
 * {@code /actuator/prometheus} as {@code resilience4j_circuitbreaker_*}.
 */
@Configuration
public class DownstreamResilience {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .slowCallRateThreshold(50f)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(4)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry,
                                                             ObjectProvider<MeterRegistry> meterRegistry) {
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        meterRegistry.ifAvailable(metrics::bindTo);
        return metrics;
    }

    public static ClientHttpRequestInterceptor forTarget(CircuitBreakerRegistry registry, String name) {
        return new CircuitBreakerInterceptor(registry.circuitBreaker(name));
    }
}
