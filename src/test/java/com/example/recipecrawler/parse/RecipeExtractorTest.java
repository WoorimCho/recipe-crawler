package com.example.recipecrawler.parse;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class RecipeExtractorTest {

    private static final LlmRecipeParser DISABLED_LLM =
            new LlmRecipeParser(FixedLlmClient.disabled(), JsonMapper.builder().build(), 12000);

    private final RecipeExtractor extractor = new RecipeExtractor(
            new SchemaOrgRecipeParser(JsonMapper.builder().build()),
            new MicrodataRecipeParser(),
            DISABLED_LLM);

    private static final String JSON_LD =
            "<script type=\"application/ld+json\">"
            + "{\"@type\":\"Recipe\",\"name\":\"From JSON-LD\",\"recipeIngredient\":[\"1 egg\"]}</script>";

    private static final String MICRODATA =
            "<div itemscope itemtype=\"http://schema.org/Recipe\">"
            + "<h1 itemprop=\"name\">From Microdata</h1>"
            + "<li itemprop=\"recipeIngredient\">1 cup flour</li></div>";

    @Test
    void prefersJsonLd() {
        assertThat(extractor.extract("<html><head>" + JSON_LD + "</head><body>" + MICRODATA + "</body></html>").name())
                .isEqualTo("From JSON-LD");
    }

    @Test
    void fallsBackToMicrodata() {
        assertThat(extractor.extract("<html><body>" + MICRODATA + "</body></html>").name())
                .isEqualTo("From Microdata");
    }

    @Test
    void extractThrowsWhenNeitherSchemaOrgStrategyMatches() {
        // extract() is the crawler's entry point — it never reaches the LLM
        assertThatThrownBy(() -> extractor.extract("<html><body><h1>nope</h1></body></html>"))
                .isInstanceOf(NoRecipeFoundException.class);
    }

    @Test
    void withNoLlmConfiguredTheFallbackIsJustExtract() {
        String noRecipe = "<html><body><h1>nope</h1></body></html>";
        Throwable viaFallback = catchThrowable(() -> extractor.extractWithLlmFallback(noRecipe));
        Throwable viaExtract = catchThrowable(() -> extractor.extract(noRecipe));
        // the schema.org "no recipe" result is re-thrown unchanged — no LLM message leaks in
        assertThat(viaFallback).isInstanceOf(NoRecipeFoundException.class)
                .hasMessage(viaExtract.getMessage());
    }

    @Test
    void llmFallbackStillPrefersSchemaOrg() {
        assertThat(extractor.extractWithLlmFallback("<html><head>" + JSON_LD + "</head><body></body></html>").name())
                .isEqualTo("From JSON-LD");
    }

    @Test
    void llmFallbackReadsAFreeTextPageWhenConfigured() {
        RecipeExtractor withLlm = new RecipeExtractor(
                new SchemaOrgRecipeParser(JsonMapper.builder().build()),
                new MicrodataRecipeParser(),
                new LlmRecipeParser(FixedLlmClient.returning(
                        "{\"name\":\"From LLM\",\"ingredients\":[\"1 egg\"],\"steps\":[\"mix\"]}"),
                        JsonMapper.builder().build(), 12000));

        String page = "<html><body><article>" + "Plenty of visible recipe prose here. ".repeat(10)
                + "</article></body></html>";
        assertThat(withLlm.extractWithLlmFallback(page).name()).isEqualTo("From LLM");
    }
}
