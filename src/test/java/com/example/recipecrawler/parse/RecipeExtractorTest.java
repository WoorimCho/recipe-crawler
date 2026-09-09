package com.example.recipecrawler.parse;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeExtractorTest {

    private final RecipeExtractor extractor = new RecipeExtractor(
            new SchemaOrgRecipeParser(JsonMapper.builder().build()),
            new MicrodataRecipeParser());

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
    void throwsWhenNeitherIsPresent() {
        assertThatThrownBy(() -> extractor.extract("<html><body><h1>nope</h1></body></html>"))
                .isInstanceOf(NoRecipeFoundException.class);
    }
}
