package com.example.recipecrawler.fetch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Downloads a recipe page as HTML. Only {@code http} / {@code https} URLs are
 * allowed; the request first checks {@code robots.txt} for our user-agent and
 * waits out the per-host politeness delay, then rides the {@code recipe-source}
 * circuit breaker + the shared HTTP timeouts and is capped at
 * {@code crawler.max-page-bytes}.
 */
@Component
public class RecipePageFetcher {

    private final RestClient http;
    private final RobotsTxt robots;
    private final HostRateLimiter rateLimiter;
    private final int maxBytes;
    private final String userAgent;

    public RecipePageFetcher(RestClient sourcePageClient,
                             RobotsTxt robots,
                             HostRateLimiter rateLimiter,
                             @Value("${crawler.max-page-bytes:3000000}") int maxBytes,
                             @Value("${crawler.user-agent:recipe-crawler/0.1 (+local import tool)}") String userAgent) {
        this.http = sourcePageClient;
        this.robots = robots;
        this.rateLimiter = rateLimiter;
        this.maxBytes = maxBytes;
        this.userAgent = userAgent;
    }

    public String fetch(String url) {
        URI uri = validate(url);
        if (!robots.isAllowed(uri)) {
            throw new DisallowedByRobotsException(uri.toString());
        }
        rateLimiter.awaitTurn(uri);
        byte[] body = http.get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                .header(HttpHeaders.USER_AGENT, userAgent)
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length == 0) {
            throw new IllegalStateException("the page returned an empty body");
        }
        int len = Math.min(body.length, maxBytes);
        return new String(body, 0, len, StandardCharsets.UTF_8);
    }

    public static URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException bad) {
            throw new IllegalArgumentException("not a valid URL: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("only http(s) URLs can be imported");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL has no host");
        }
        return uri;
    }

    /** {@code robots.txt} forbids fetching this URL with our user-agent. */
    public static class DisallowedByRobotsException extends RuntimeException {
        public DisallowedByRobotsException(String url) {
            super("robots.txt disallows " + url);
        }
    }
}
