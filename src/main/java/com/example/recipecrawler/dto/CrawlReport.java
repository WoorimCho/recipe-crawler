package com.example.recipecrawler.dto;

import java.util.List;

/**
 * Outcome of a crawl.
 *
 * @param pagesFetched  pages actually downloaded
 * @param recipesFound  pages that had a schema.org Recipe
 * @param imported      recipes created (or, on a dry run, that would be — {@code recipeId} 0)
 * @param skipped       URLs not imported, with why (robots, unreachable, already imported, …)
 * @param stoppedBecause why the crawl ended ("queue exhausted", "hit maxPages", …)
 */
public record CrawlReport(
        List<String> seeds,
        int pagesFetched,
        int recipesFound,
        List<Item> imported,
        List<Skip> skipped,
        String stoppedBecause) {

    public record Item(String url, long recipeId, String name,
                       int ingredientCount, int ingredientsCreated, boolean created) {
    }

    public record Skip(String url, String reason) {
    }
}
