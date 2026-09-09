package com.example.recipecrawler.web;

import com.example.recipecrawler.dto.CrawlReport;
import com.example.recipecrawler.dto.CrawlRequest;
import com.example.recipecrawler.dto.ImportRequest;
import com.example.recipecrawler.dto.ImportResponse;
import com.example.recipecrawler.dto.RecipeDraft;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * <ul>
 *   <li>{@code POST /api/import/preview} — fetch + parse one recipe URL, return
 *       the draft (ingredients parsed, matched against IngredientCatalogue).
 *       Writes nothing.</li>
 *   <li>{@code POST /api/import/commit} — the same, then create any missing
 *       ingredients and the recipe.</li>
 *   <li>{@code POST /api/crawl} — breadth-first from seed URLs: robots.txt +
 *       politeness honoured, same-host link-following, import every recipe found.</li>
 * </ul>
 */
@RestController
public class ImportController {

    private final ImportService imports;
    private final CrawlService crawls;

    public ImportController(ImportService imports, CrawlService crawls) {
        this.imports = imports;
        this.crawls = crawls;
    }

    @PostMapping("/api/import/preview")
    public RecipeDraft preview(@Valid @RequestBody ImportRequest request) {
        return imports.preview(request.url());
    }

    @PostMapping("/api/import/commit")
    public ImportResponse commit(@Valid @RequestBody ImportRequest request) {
        return imports.commit(request.url(), request.creator());
    }

    @PostMapping("/api/crawl")
    public CrawlReport crawl(@Valid @RequestBody CrawlRequest request) {
        return crawls.crawl(request);
    }
}
