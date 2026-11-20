package com.example.recipecrawler.fetch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal {@code robots.txt} support. Fetches and caches one file per host, then
 * answers {@link #isAllowed} for our user-agent (falling back to the {@code *}
 * group). Longest matching rule wins; on a tie {@code Allow} beats {@code Disallow}.
 *
 * <p>Fetch outcomes follow the common convention: a 4xx / missing file → allow
 * everything; a 5xx / network error → allow nothing for that host (fail closed).
 */
@Component
public class RobotsTxt {

    private final RestClient http;
    private final String userAgent;
    private final Map<String, Rules> cache = new ConcurrentHashMap<>();

    public RobotsTxt(RestClient sourcePageClient,
                     @Value("${crawler.user-agent:RecipeCrawler}") String userAgent) {
        this.http = sourcePageClient;
        this.userAgent = userAgent.toLowerCase(Locale.ROOT);
    }

    public boolean isAllowed(URI uri) {
        if (uri.getHost() == null) {
            return false;
        }
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        Rules rules = cache.computeIfAbsent(origin, this::load);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }
        return rules.allows(path);
    }

    private Rules load(String origin) {
        String body;
        try {
            body = http.get().uri(origin + "/robots.txt")
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException status) {
            // 4xx (incl. 404) → no restrictions; other statuses → be cautious
            return status.getStatusCode().is4xxClientError() ? Rules.ALLOW_ALL : Rules.DENY_ALL;
        } catch (RuntimeException networkOrTimeout) {
            return Rules.DENY_ALL;
        }
        return Rules.parse(body == null ? "" : body, userAgent);
    }

    // ── rules ────────────────────────────────────────────────────────────────

    static final class Rules {
        static final Rules ALLOW_ALL = new Rules(List.of(), List.of());
        static final Rules DENY_ALL = new Rules(List.of("/"), List.of());

        private final List<String> disallow;
        private final List<String> allow;

        Rules(List<String> disallow, List<String> allow) {
            this.disallow = disallow;
            this.allow = allow;
        }

        boolean allows(String path) {
            String da = longestMatch(disallow, path);
            String al = longestMatch(allow, path);
            if (da == null) {
                return true;
            }
            if (al == null) {
                return false;
            }
            return al.length() >= da.length();      // tie → Allow wins
        }

        private static String longestMatch(List<String> patterns, String path) {
            String best = null;
            for (String p : patterns) {
                if (matches(p, path) && (best == null || p.length() > best.length())) {
                    best = p;
                }
            }
            return best;
        }

        /** Supports the {@code *} wildcard and a trailing {@code $} anchor; prefix match otherwise. */
        private static boolean matches(String pattern, String path) {
            if (pattern.isEmpty()) {
                return false;
            }
            boolean anchored = pattern.endsWith("$");
            String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
            String[] parts = p.split("\\*", -1);
            int idx = 0;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) {
                    continue;
                }
                int found = path.indexOf(part, idx);
                if (found < 0) {
                    return false;
                }
                if (i == 0 && found != 0) {
                    return false;                    // first literal must be a prefix
                }
                idx = found + part.length();
            }
            return !anchored || idx == path.length();
        }

        static Rules parse(String body, String ua) {
            // group the file by User-agent; collect our group, else the "*" group
            List<String> starDis = new ArrayList<>(), starAllow = new ArrayList<>();
            List<String> uaDis = new ArrayList<>(), uaAllow = new ArrayList<>();
            List<String> curAgents = new ArrayList<>();
            boolean lastWasDirective = false;

            for (String rawLine : body.split("\\r?\\n")) {
                String line = rawLine;
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();

                if (field.equals("user-agent")) {
                    if (lastWasDirective) {
                        curAgents.clear();
                    }
                    curAgents.add(value.toLowerCase(Locale.ROOT));
                    lastWasDirective = false;
                } else if (field.equals("disallow") || field.equals("allow")) {
                    lastWasDirective = true;
                    for (String agent : curAgents) {
                        boolean isStar = agent.equals("*");
                        boolean isUs = !isStar && ua.contains(agent);
                        if (!isStar && !isUs) {
                            continue;
                        }
                        List<String> target = field.equals("disallow")
                                ? (isStar ? starDis : uaDis)
                                : (isStar ? starAllow : uaAllow);
                        if (!value.isEmpty()) {
                            target.add(value);
                        }
                    }
                }
            }
            // our own group wins outright if it exists
            if (!uaDis.isEmpty() || !uaAllow.isEmpty()) {
                return new Rules(uaDis, uaAllow);
            }
            return new Rules(starDis, starAllow);
        }
    }
}
