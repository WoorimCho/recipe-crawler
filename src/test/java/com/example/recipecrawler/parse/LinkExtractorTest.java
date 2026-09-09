package com.example.recipecrawler.parse;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LinkExtractorTest {

    private final LinkExtractor extractor = new LinkExtractor();

    private static final String PAGE = """
            <html><body>
              <a href="/recipes/cake">cake</a>
              <a href="recipes/pie">pie (relative)</a>
              <a href="https://site.test/recipes/tart#reviews">tart with fragment</a>
              <a href="https://other.test/recipes/flan">off-site</a>
              <a href="/assets/logo.png">image</a>
              <a href="/wp-login.php">login</a>
              <a href="mailto:chef@site.test">email</a>
              <a href="/recipes/cake">cake again (dup)</a>
            </body></html>""";

    @Test
    void resolvesRelativeKeepsSameHostDropsJunkAndDeduplicates() {
        List<URI> links = extractor.extract(PAGE, "https://site.test/index.html", Set.of("site.test"));

        assertThat(links).extracting(URI::toString).containsExactlyInAnyOrder(
                "https://site.test/recipes/cake",
                "https://site.test/recipes/pie",
                "https://site.test/recipes/tart");     // fragment stripped
        // off-site, .png, wp-login, mailto all filtered; the duplicate collapsed
    }

    @Test
    void emptyHostFilterAllowsAnyHost() {
        List<URI> links = extractor.extract(PAGE, "https://site.test/index.html", Set.of());
        assertThat(links).extracting(URI::toString).contains("https://other.test/recipes/flan");
    }

    @Test
    void normaliseRejectsNonHttp() {
        assertThat(LinkExtractor.normalise("mailto:x@y.com")).isNull();
        assertThat(LinkExtractor.normalise("javascript:void(0)")).isNull();
        assertThat(LinkExtractor.normalise("ftp://host/f")).isNull();
        assertThat(LinkExtractor.normalise("https://ok.test/a#frag").getRawFragment()).isNull();
    }
}
