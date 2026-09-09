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
 * allowed, the response is capped at {@code crawler.max-page-bytes}, and the
 * request rides the {@code recipe-source} circuit breaker + the shared HTTP
 * timeouts so a slow or hostile page can't hang the importer.
 */
@Component
public class RecipePageFetcher {

    private final RestClient http;
    private final int maxBytes;

    public RecipePageFetcher(RestClient sourcePageClient,
                             @Value("${crawler.max-page-bytes:3000000}") int maxBytes) {
        this.http = sourcePageClient;
        this.maxBytes = maxBytes;
    }

    public String fetch(String url) {
        URI uri = validate(url);
        byte[] body = http.get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                .header(HttpHeaders.USER_AGENT, "recipe-crawler/0.0.1 (+local import tool)")
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length == 0) {
            throw new IllegalStateException("the page returned an empty body");
        }
        int len = Math.min(body.length, maxBytes);
        return new String(body, 0, len, StandardCharsets.UTF_8);
    }

    private static URI validate(String url) {
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
}
