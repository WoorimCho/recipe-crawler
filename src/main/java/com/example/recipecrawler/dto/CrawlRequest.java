package com.example.recipecrawler.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

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
        @NotEmpty @Size(max = Limits.SEEDS) List<@Size(max = Limits.URL_LENGTH) String> seeds,
        Integer maxPages,
        Integer maxDepth,
        Boolean sameHostOnly,
        Boolean dryRun,
        @Size(max = Limits.CREATOR_LENGTH) String creator) {
}
