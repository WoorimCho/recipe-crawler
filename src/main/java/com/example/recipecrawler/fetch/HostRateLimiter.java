package com.example.recipecrawler.fetch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Politeness delay: at most one request per host per {@code crawler.politeness-delay}.
 * {@link #awaitTurn} blocks the calling thread until that host's next slot.
 */
@Component
public class HostRateLimiter {

    private final long delayMillis;
    private final Map<String, Long> nextAllowed = new ConcurrentHashMap<>();

    public HostRateLimiter(@Value("${crawler.politeness-delay:1500ms}") Duration delay) {
        this.delayMillis = Math.max(delay.toMillis(), 0);
    }

    public void awaitTurn(URI uri) {
        String host = uri.getHost();
        if (host == null || delayMillis == 0) {
            return;
        }
        long waitFor;
        synchronized (nextAllowed) {
            long now = System.currentTimeMillis();
            long slot = nextAllowed.getOrDefault(host, 0L);
            long start = Math.max(now, slot);
            nextAllowed.put(host, start + delayMillis);
            waitFor = start - now;
        }
        if (waitFor > 0) {
            try {
                Thread.sleep(waitFor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
