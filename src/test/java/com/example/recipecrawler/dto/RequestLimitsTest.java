package com.example.recipecrawler.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** SECURITY.md N4: the input of an import or crawl is bounded, not just the crawl itself. */
class RequestLimitsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    private static List<String> seeds(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "https://example.com/" + i).collect(Collectors.toList());
    }

    private static CrawlRequest crawl(List<String> seeds) {
        return new CrawlRequest(seeds, null, null, null, null, null);
    }

    private static Set<String> paths(Object bean) {
        return validator.validate(bean).stream().map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    void aCrawlWithTheMaximumNumberOfSeedsIsValid() {
        assertThat(validator.validate(crawl(seeds(Limits.SEEDS)))).isEmpty();
    }

    @Test
    void oneSeedTooMany() {
        assertThat(paths(crawl(seeds(Limits.SEEDS + 1)))).containsExactly("seeds");
    }

    @Test
    void anOverlongSeedUrl() {
        String url = "https://example.com/" + "a".repeat(Limits.URL_LENGTH);
        assertThat(paths(crawl(List.of(url)))).hasSize(1).allMatch(p -> p.startsWith("seeds"));
    }

    @Test
    void anEmptySeedListIsStillRejected() {
        assertThat(paths(crawl(List.of()))).containsExactly("seeds");
    }

    @Test
    void importUrlAndCreatorAreBounded() {
        assertThat(validator.validate(new ImportRequest("https://example.com/r", "me"))).isEmpty();
        assertThat(paths(new ImportRequest("https://example.com/" + "a".repeat(Limits.URL_LENGTH), null)))
                .containsExactly("url");
        assertThat(paths(new ImportRequest("https://example.com/r", "c".repeat(Limits.CREATOR_LENGTH + 1))))
                .containsExactly("creator");
    }
}
