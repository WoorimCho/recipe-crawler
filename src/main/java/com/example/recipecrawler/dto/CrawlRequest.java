package com.example.recipecrawler.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Start a crawl from one or more seed pages.
 *
 * @param seeds        pages to start from (a recipe page or a listing / index)
 * @param maxPages     hard cap on pages fetched (default {@code crawler.max-pages})
 * @param maxDepth     link-follow depth from the seeds (default {@code crawler.max-depth}; 0 = seeds only)
 * @param sameHostOnly stay on the seed hosts (default true)
 * @param dryRun       find recipes but write nothing (default false)
 * @param creator      overrides the {@code import:<host>} creator on imported recipes
 */
public record CrawlRequest(
        @NotEmpty List<String> seeds,
        Integer maxPages,
        Integer maxDepth,
        Boolean sameHostOnly,
        Boolean dryRun,
        String creator) {
}
