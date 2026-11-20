package com.example.recipecrawler.parse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRecipeParserTest {

    private static final String PAGE = "<html><body><article><h1>Grandma's Cornbread</h1><p>"
            + "A short story about cornbread that goes on for a while so there is plenty of "
            + "visible text on the page for the fallback to work with. ".repeat(4)
            + "You will need cornmeal and buttermilk and an egg. Mix and bake.</p></article></body></html>";

    private LlmRecipeParser parser(LlmClient llm) {
        return new LlmRecipeParser(llm, JsonMapper.builder().build(), 12000);
    }

    @Test
    void skippedWhenNotConfigured() {
        assertThatThrownBy(() -> parser(FixedLlmClient.disabled()).parse(PAGE))
                .isInstanceOf(NoRecipeFoundException.class)
                .hasMessageContaining("no LLM configured");
    }

    @Test
    void skippedWhenThePageHasBarelyAnyText() {
        assertThatThrownBy(() -> parser(FixedLlmClient.returning("{}")).parse("<html><body>hi</body></html>"))
                .isInstanceOf(NoRecipeFoundException.class)
                .hasMessageContaining("too little text");
    }

    @Test
    void parsesAPlainJsonObject() {
        LlmRecipeParser p = parser(FixedLlmClient.returning(
                "{\"name\":\"Cornbread\",\"yield\":\"1 pan\","
                        + "\"ingredients\":[\"1 cup cornmeal\",\"1 cup buttermilk\",\"1 egg\"],"
                        + "\"steps\":[\"Mix.\",\"Bake at 400F for 25 min.\"]}"));

        ParsedRecipe r = p.parse(PAGE);

        assertThat(r.name()).isEqualTo("Cornbread");
        assertThat(r.yield()).isEqualTo("1 pan");
        assertThat(r.ingredients()).containsExactly("1 cup cornmeal", "1 cup buttermilk", "1 egg");
        assertThat(r.steps()).hasSize(2);
    }

    @Test
    void toleratesCodeFencesAndPreamble() {
        LlmRecipeParser p = parser(FixedLlmClient.returning(
                "Sure! Here is the recipe:\n```json\n"
                        + "{\"name\":\"Cornbread\",\"ingredients\":[\"cornmeal\"],\"steps\":[]}\n```\n"));

        assertThat(p.parse(PAGE).name()).isEqualTo("Cornbread");
    }

    @Test
    void notARecipeSentinelBecomes422() {
        assertThatThrownBy(() -> parser(FixedLlmClient.returning(
                "{\"name\":null,\"yield\":null,\"ingredients\":[],\"steps\":[]}")).parse(PAGE))
                .isInstanceOf(NoRecipeFoundException.class)
                .hasMessageContaining("could not find a recipe");
    }

    @Test
    void emptyIngredientListBecomes422() {
        assertThatThrownBy(() -> parser(FixedLlmClient.returning(
                "{\"name\":\"Water\",\"ingredients\":[],\"steps\":[\"pour\"]}")).parse(PAGE))
                .isInstanceOf(NoRecipeFoundException.class);
    }

    @Test
    void unparseableJsonBecomes422() {
        assertThatThrownBy(() -> parser(FixedLlmClient.returning("not json at all")).parse(PAGE))
                .isInstanceOf(NoRecipeFoundException.class)
                .hasMessageContaining("wasn't valid recipe JSON");
    }

    @Test
    void httpErrorFromTheProviderBecomes422() {
        LlmClient llm = FixedLlmClient.throwing(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        assertThatThrownBy(() -> parser(llm).parse(PAGE))
                .isInstanceOf(NoRecipeFoundException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void transportFailurePropagates() {
        LlmClient llm = FixedLlmClient.throwing(new ResourceAccessException("llm is unavailable (circuit OPEN)"));

        assertThatThrownBy(() -> parser(llm).parse(PAGE))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void extractsTheOutermostBraces() {
        assertThat(LlmRecipeParser.extractJsonObject("junk {\"a\":{\"b\":1}} trailing")).isEqualTo("{\"a\":{\"b\":1}}");
        assertThat(LlmRecipeParser.extractJsonObject("no braces here")).isEqualTo("no braces here");
    }
}
