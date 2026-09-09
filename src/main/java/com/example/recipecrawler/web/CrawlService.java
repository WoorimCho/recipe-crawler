package com.example.recipecrawler.web;

import com.example.recipecrawler.catalogue.CatalogueGateway;
import com.example.recipecrawler.dto.CrawlReport;
import com.example.recipecrawler.dto.CrawlRequest;
import com.example.recipecrawler.dto.ImportResponse;
import com.example.recipecrawler.fetch.RecipePageFetcher;
import com.example.recipecrawler.parse.LinkExtractor;
import com.example.recipecrawler.parse.SchemaOrgRecipeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Breadth-first crawl from the seed URLs. Per host it honours {@code robots.txt}
 * and a politeness delay (both in {@link RecipePageFetcher}). Every page that
 * carries a schema.org Recipe is imported (or, on a dry run, just reported);
 * other pages are scanned for same-host links to follow, bounded by
 * {@code maxPages} / {@code maxDepth}. Each URL is visited at most once, and a
 * recipe whose {@code name}+{@code creator} already exists is skipped.
 */
@Service
public class CrawlService {

    private final RecipePageFetcher fetcher;
    private final SchemaOrgRecipeParser recipeParser;
    private final LinkExtractor linkExtractor;
    private final ImportService imports;
    private final CatalogueGateway catalogue;
    private final int defaultMaxPages;
    private final int defaultMaxDepth;

    public CrawlService(RecipePageFetcher fetcher, SchemaOrgRecipeParser recipeParser,
                        LinkExtractor linkExtractor, ImportService imports, CatalogueGateway catalogue,
                        @Value("${crawler.max-pages:25}") int defaultMaxPages,
                        @Value("${crawler.max-depth:2}") int defaultMaxDepth) {
        this.fetcher = fetcher;
        this.recipeParser = recipeParser;
        this.linkExtractor = linkExtractor;
        this.imports = imports;
        this.catalogue = catalogue;
        this.defaultMaxPages = defaultMaxPages;
        this.defaultMaxDepth = defaultMaxDepth;
    }

    public CrawlReport crawl(CrawlRequest req) {
        int maxPages = clamp(req.maxPages() != null ? req.maxPages() : defaultMaxPages, 1, 200);
        int maxDepth = clamp(req.maxDepth() != null ? req.maxDepth() : defaultMaxDepth, 0, 5);
        boolean sameHostOnly = req.sameHostOnly() == null || req.sameHostOnly();
        boolean dryRun = Boolean.TRUE.equals(req.dryRun());

        Set<String> allowedHosts = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>();
        List<CrawlReport.Skip> skipped = new ArrayList<>();

        for (String seed : req.seeds()) {
            URI u;
            try {
                u = RecipePageFetcher.validate(seed);
            } catch (RuntimeException bad) {
                skipped.add(new CrawlReport.Skip(seed, bad.getMessage()));
                continue;
            }
            if (sameHostOnly) {
                allowedHosts.add(u.getHost().toLowerCase(Locale.ROOT));
            }
            if (seen.add(canon(u))) {
                queue.add(new Node(u, 0));
            }
        }

        List<CrawlReport.Item> imported = new ArrayList<>();
        int fetched = 0;
        int found = 0;
        String stopped = "queue exhausted";

        while (!queue.isEmpty()) {
            if (fetched >= maxPages) {
                stopped = "hit maxPages (" + maxPages + ")";
                break;
            }
            Node node = queue.poll();
            String html;
            try {
                html = fetcher.fetch(node.uri.toString());
                fetched++;
            } catch (RecipePageFetcher.DisallowedByRobotsException robots) {
                skipped.add(new CrawlReport.Skip(node.uri.toString(), "robots.txt"));
                continue;
            } catch (RuntimeException e) {
                skipped.add(new CrawlReport.Skip(node.uri.toString(), reason(e)));
                continue;
            }

            SchemaOrgRecipeParser.Result recipe;
            try {
                recipe = recipeParser.parse(html);
            } catch (SchemaOrgRecipeParser.NoRecipeFoundException notARecipe) {
                recipe = null;
            }

            if (recipe != null) {
                found++;
                handleRecipe(node.uri.toString(), html, recipe, req.creator(), dryRun, imported, skipped);
            }

            if (node.depth < maxDepth) {
                Set<String> hostFilter = sameHostOnly ? allowedHosts : Set.of();
                for (URI link : linkExtractor.extract(html, node.uri.toString(), hostFilter)) {
                    if (seen.size() > maxPages * 6) {
                        break;                          // keep the frontier bounded
                    }
                    if (seen.add(canon(link))) {
                        queue.add(new Node(link, node.depth + 1));
                    }
                }
            }
        }
        return new CrawlReport(req.seeds(), fetched, found, imported, skipped, stopped);
    }

    private void handleRecipe(String url, String html, SchemaOrgRecipeParser.Result recipe,
                              String creatorOverride, boolean dryRun,
                              List<CrawlReport.Item> imported, List<CrawlReport.Skip> skipped) {
        try {
            if (dryRun) {
                imported.add(new CrawlReport.Item(url, 0, recipe.name(),
                        recipe.ingredients().size(), 0, false));
                return;
            }
            String creator = imports.creatorFor(url, creatorOverride);
            if (catalogue.recipeExists(recipe.name(), creator)) {
                skipped.add(new CrawlReport.Skip(url, "already imported (" + recipe.name() + ")"));
                return;
            }
            ImportResponse resp = imports.commitHtml(url, html, creatorOverride);
            imported.add(new CrawlReport.Item(url, resp.recipeId(), resp.name(),
                    resp.ingredientCount(), resp.ingredientsCreated(), true));
        } catch (RuntimeException e) {
            skipped.add(new CrawlReport.Skip(url, "import failed: " + reason(e)));
        }
    }

    private static String reason(RuntimeException e) {
        if (e instanceof RestClientResponseException status) {
            return "HTTP " + status.getStatusCode().value();
        }
        if (e instanceof ResourceAccessException) {
            return "unreachable / timeout";
        }
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : (m.length() > 120 ? m.substring(0, 120) + "…" : m);
    }

    private static String canon(URI u) {
        String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String q = u.getRawQuery() == null ? "" : "?" + u.getRawQuery();
        return u.getHost().toLowerCase(Locale.ROOT) + path + q;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private record Node(URI uri, int depth) {
    }
}
