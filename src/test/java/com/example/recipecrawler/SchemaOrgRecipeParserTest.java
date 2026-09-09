package com.example.recipecrawler;

import com.example.recipecrawler.parse.SchemaOrgRecipeParser;
import com.example.recipecrawler.parse.SchemaOrgRecipeParser.NoRecipeFoundException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaOrgRecipeParserTest {

    private final SchemaOrgRecipeParser parser = new SchemaOrgRecipeParser(JsonMapper.builder().build());

    private static String page(String jsonLd) {
        return "<html><head><script type=\"application/ld+json\">" + jsonLd
                + "</script></head><body>...</body></html>";
    }

    @Test
    void readsAGraphWrappedRecipeWithHowToSteps() {
        String jsonLd = """
                {"@context":"https://schema.org","@graph":[
                  {"@type":"WebPage","name":"ignore me"},
                  {"@type":"Recipe","name":"Banana Bread","recipeYield":"1 loaf",
                   "recipeIngredient":["2 cups flour","1 tsp baking soda","3 ripe bananas"],
                   "recipeInstructions":[
                     {"@type":"HowToStep","text":"Mash the bananas."},
                     {"@type":"HowToStep","text":"Fold in the flour."}]}]}""";
        SchemaOrgRecipeParser.Result r = parser.parse(page(jsonLd));

        assertThat(r.name()).isEqualTo("Banana Bread");
        assertThat(r.yield()).isEqualTo("1 loaf");
        assertThat(r.ingredients()).containsExactly("2 cups flour", "1 tsp baking soda", "3 ripe bananas");
        assertThat(r.steps()).containsExactly("Mash the bananas.", "Fold in the flour.");
    }

    @Test
    void readsAnArrayOfNodesAndStringInstructions() {
        String jsonLd = """
                [{"@type":"Organization","name":"Site"},
                 {"@type":["Recipe"],"name":"Toast &amp; Jam",
                  "recipeIngredient":["1 slice bread","1 tbsp jam"],
                  "recipeInstructions":"Toast the bread.\\nSpread the jam."}]""";
        SchemaOrgRecipeParser.Result r = parser.parse(page(jsonLd));

        assertThat(r.name()).isEqualTo("Toast & Jam");
        assertThat(r.ingredients()).hasSize(2);
        assertThat(r.steps()).containsExactly("Toast the bread.", "Spread the jam.");
    }

    @Test
    void readsHowToSectionNesting() {
        String jsonLd = """
                {"@type":"Recipe","name":"Two Part","recipeIngredient":["a"],
                 "recipeInstructions":[
                   {"@type":"HowToSection","itemListElement":[
                     {"@type":"HowToStep","text":"Step one."},
                     {"@type":"HowToStep","text":"Step two."}]}]}""";
        assertThat(parser.parse(page(jsonLd)).steps()).containsExactly("Step one.", "Step two.");
    }

    @Test
    void throwsWhenNoRecipeJsonLd() {
        assertThatThrownBy(() -> parser.parse("<html><body><h1>just a page</h1></body></html>"))
                .isInstanceOf(NoRecipeFoundException.class);
        assertThatThrownBy(() -> parser.parse(page("{\"@type\":\"Article\",\"name\":\"nope\"}")))
                .isInstanceOf(NoRecipeFoundException.class);
    }

    @Test
    void toleratesMalformedJsonLdBlocksAndUsesTheGoodOne() {
        String html = "<html><head>"
                + "<script type=\"application/ld+json\">{ this is not json </script>"
                + "<script type=\"application/ld+json\">"
                + "{\"@type\":\"Recipe\",\"name\":\"Survivor\",\"recipeIngredient\":[\"1 egg\"]}"
                + "</script></head><body></body></html>";
        assertThat(parser.parse(html).name()).isEqualTo("Survivor");
    }
}
