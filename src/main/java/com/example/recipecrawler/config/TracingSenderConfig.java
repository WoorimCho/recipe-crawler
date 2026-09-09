package com.example.recipecrawler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Replaces Boot 4.1's JDK-HttpClient Zipkin sender (which fails on every flush in
 * a container network) with the plain {@code HttpURLConnection}-based one.
 * Mirrors the same bean in the BFF and the three catalogue services.
 */
@Configuration
public class TracingSenderConfig {

    @Bean
    BytesMessageSender zipkinSender(
            @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}") String endpoint) {
        return URLConnectionSender.create(endpoint);
    }
}
