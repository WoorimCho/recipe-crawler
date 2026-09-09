package com.example.recipecrawler;

import com.example.recipecrawler.catalogue.CatalogueGateway;
import com.example.recipecrawler.dto.CrawlReport;
import com.example.recipecrawler.dto.CrawlRequest;
import com.example.recipecrawler.dto.ImportResponse;
import com.example.recipecrawler.fetch.RecipePageFetcher;
import com.example.recipecrawler.parse.LinkExtractor;
import com.example.recipecrawler.parse.MicrodataRecipeParser;
import com.example.recipecrawler.parse.RecipeExtractor;
import com.example.recipecrawler.parse.SchemaOrgRecipeParser;
import com.example.recipecrawler.web.CrawlService;
import com.example.recipecrawler.web.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlServiceTest {

    private RecipePageFetcher fetcher;
    private ImportService imports;
    private CatalogueGateway catalogue;
    private CrawlService crawl;

    private static String recipePage(String name) {
        return "<html><head><script type=\"application/ld+json\">"
                + "{\"@type\":\"Recipe\",\"name\":\"" + name + "\",\"recipeIngredient\":[\"1 egg\",\"2 cups flour\"]}"
                + "</script></head><body>recipe</body></html>";
    }

    private static final String LISTING = """
            <html><body>
              <a href="/r/cake">cake</a>
              <a href="/r/pie">pie</a>
              <a href="https://elsewhere.test/r/flan">off-site, ignored</a>
            </body></html>""";

    @BeforeEach
    void setUp() {
        fetcher = mock(RecipePageFetcher.class);
        imports = mock(ImportService.class);
        catalogue = mock(CatalogueGateway.class);
        crawl = new CrawlService(fetcher, new RecipeExtractor(new SchemaOrgRecipeParser(JsonMapper.builder().build()), new MicrodataRecipeParser()),
                new LinkExtractor(), imports, catalogue, 25, 2);

        when(fetcher.fetch("https://site.test/recipes")).thenReturn(LISTING);
        when(fetcher.fetch("https://site.test/r/cake")).thenReturn(recipePage("Cake"));
        when(fetcher.fetch("https://site.test/r/pie")).thenReturn(recipePage("Pie"));
    }

    private CrawlRequest req(Integer maxPages, Integer maxDepth, Boolean dryRun) {
        return new CrawlRequest(List.of("https://site.test/recipes"), maxPages, maxDepth,
                true, dryRun, null);
    }

    @Test
    void dryRunFindsRecipesBehindLinksAndWritesNothing() {
        CrawlReport r = crawl.crawl(req(25, 1, true));

        assertThat(r.pagesFetched()).isEqualTo(3);             // listing + 2 recipes
        assertThat(r.recipesFound()).isEqualTo(2);
        assertThat(r.imported()).extracting(CrawlReport.Item::name)
                .containsExactlyInAnyOrder("Cake", "Pie");
        assertThat(r.imported()).allMatch(i -> i.recipeId() == 0 && !i.created());
        verify(imports, never()).commitHtml(anyString(), anyString(), any());
    }

    @Test
    void realRunImportsEachNewRecipeAndSkipsDuplicates() {
        when(imports.creatorFor(anyString(), any())).thenReturn("import:site.test");
        when(catalogue.recipeExists(eq("Cake"), anyString())).thenReturn(false);
        when(catalogue.recipeExists(eq("Pie"), anyString())).thenReturn(true);   // already have this one
        when(imports.commitHtml(eq("https://site.test/r/cake"), anyString(), any()))
                .thenReturn(new ImportResponse(101, "Cake", 2, 1, 1, "https://site.test/r/cake"));

        CrawlReport r = crawl.crawl(req(25, 1, false));

        assertThat(r.imported()).singleElement()
                .satisfies(i -> {
                    assertThat(i.name()).isEqualTo("Cake");
                    assertThat(i.recipeId()).isEqualTo(101);
                    assertThat(i.created()).isTrue();
                });
        assertThat(r.skipped()).extracting(CrawlReport.Skip::reason)
                .anyMatch(s -> s.contains("already imported"));
        verify(imports, never()).commitHtml(eq("https://site.test/r/pie"), anyString(), any());
    }

    @Test
    void robotsDisallowIsReportedNotFatal() {
        when(fetcher.fetch("https://site.test/r/pie"))
                .thenThrow(new RecipePageFetcher.DisallowedByRobotsException("https://site.test/r/pie"));

        CrawlReport r = crawl.crawl(req(25, 1, true));

        assertThat(r.imported()).extracting(CrawlReport.Item::name).containsExactly("Cake");
        assertThat(r.skipped()).extracting(CrawlReport.Skip::reason).contains("robots.txt");
        assertThat(r.pagesFetched()).isEqualTo(2);             // pie never counted
    }

    @Test
    void maxPagesStopsTheCrawl() {
        CrawlReport r = crawl.crawl(req(2, 1, true));

        assertThat(r.pagesFetched()).isEqualTo(2);
        assertThat(r.stoppedBecause()).contains("maxPages");
    }

    @Test
    void depthZeroImportsOnlyTheSeed() {
        when(fetcher.fetch("https://site.test/recipes")).thenReturn(recipePage("Seed Recipe"));

        CrawlReport r = crawl.crawl(req(25, 0, true));

        assertThat(r.pagesFetched()).isEqualTo(1);
        assertThat(r.imported()).extracting(CrawlReport.Item::name).containsExactly("Seed Recipe");
    }
}
