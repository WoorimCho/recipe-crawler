package com.example.recipecrawler.dto;

/**
 * Upper bounds on a request body (SECURITY.md N4). The crawl itself is already
 * bounded by {@code maxPages} / {@code maxDepth} / page-size caps in
 * {@code CrawlService}; these stop the <em>input</em> from being the unbounded part.
 */
public final class Limits {

    public static final int SEEDS = 20;
    public static final int URL_LENGTH = 2_048;
    public static final int CREATOR_LENGTH = 255;   // recipe.creator varchar(255) downstream

    private Limits() {
    }
}
