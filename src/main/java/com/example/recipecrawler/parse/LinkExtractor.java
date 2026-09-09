package com.example.recipecrawler.parse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pulls followable links out of a page: {@code <a href>}s resolved against the
 * page URL, http(s) only, fragment stripped, de-duplicated, and filtered to the
 * given hosts. Obvious non-article links (assets, auth, feeds, wp-admin) are
 * dropped; listing pages (tag / category / archive) are kept — that's where the
 * recipe links live.
 */
@Component
public class LinkExtractor {

    private static final Set<String> SKIP_SUFFIX = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico", ".css", ".js",
            ".pdf", ".zip", ".mp4", ".mp3", ".xml", ".rss", ".json");

    private static final List<String> SKIP_CONTAINS = List.of(
            "/wp-admin", "/wp-login", "/xmlrpc.php", "/cart", "/checkout", "/account",
            "/login", "/signin", "/signup", "/register", "/feed", "?share=", "?replytocom=");

    /** @param allowedHosts lower-case hosts a link may point at; empty = any host */
    public List<URI> extract(String html, String pageUrl, Set<String> allowedHosts) {
        Set<URI> out = new LinkedHashSet<>();
        var doc = Jsoup.parse(html, pageUrl);
        for (Element a : doc.select("a[href]")) {
            String abs = a.absUrl("href");
            if (abs.isEmpty()) {
                continue;
            }
            URI uri = normalise(abs);
            if (uri == null) {
                continue;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!allowedHosts.isEmpty() && !allowedHosts.contains(host)) {
                continue;
            }
            if (isJunk(uri)) {
                continue;
            }
            out.add(uri);
        }
        return List.copyOf(out);
    }

    static URI normalise(String raw) {
        try {
            URI u = URI.create(raw).normalize();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || u.getHost() == null) {
                return null;
            }
            // drop the fragment; keep path + query
            String q = u.getRawQuery();
            return new URI(scheme, u.getAuthority(), u.getRawPath(), q, null);
        } catch (RuntimeException | java.net.URISyntaxException e) {
            return null;
        }
    }

    private static boolean isJunk(URI uri) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().toLowerCase(Locale.ROOT);
        for (String suf : SKIP_SUFFIX) {
            if (path.endsWith(suf)) {
                return true;
            }
        }
        String full = (path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())).toLowerCase(Locale.ROOT);
        for (String frag : SKIP_CONTAINS) {
            if (full.contains(frag)) {
                return true;
            }
        }
        return false;
    }
}
