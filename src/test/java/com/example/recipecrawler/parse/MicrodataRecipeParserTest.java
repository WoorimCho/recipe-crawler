package com.example.recipecrawler.parse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrodataRecipeParserTest {

    private final MicrodataRecipeParser parser = new MicrodataRecipeParser();

    private static final String MICRODATA = """
            <html><body>
            <div itemscope itemtype="http://schema.org/Recipe">
              <h1 itemprop="name">Pancakes</h1>
              <span itemprop="recipeYield">12 pancakes</span>
              <ul>
                <li itemprop="recipeIngredient">2 cups flour</li>
                <li itemprop="recipeIngredient">1 &frac12; cups milk</li>
                <li itemprop="recipeIngredient">2 eggs</li>
              </ul>
              <div itemprop="recipeInstructions">
                <ol>
                  <li>Whisk the dry ingredients.</li>
                  <li>Add the wet, stir, rest 10 minutes.</li>
                  <li>Cook on a hot griddle.</li>
                </ol>
              </div>
              <div itemprop="author" itemscope itemtype="http://schema.org/Person">
                <span itemprop="name">Not The Recipe Name</span>
              </div>
            </div>
            </body></html>""";

    @Test
    void readsNameYieldIngredientsAndSteps() {
        ParsedRecipe r = parser.parse(MICRODATA);

        assertThat(r.name()).isEqualTo("Pancakes");            // not the nested author name
        assertThat(r.yield()).isEqualTo("12 pancakes");
        assertThat(r.ingredients()).containsExactly("2 cups flour", "1 ½ cups milk", "2 eggs");
        assertThat(r.steps()).containsExactly(
                "Whisk the dry ingredients.",
                "Add the wet, stir, rest 10 minutes.",
                "Cook on a hot griddle.");
    }

    @Test
    void readsMetaContentAndHttpsItemtype() {
        String html = """
                <div itemscope itemtype="https://schema.org/Recipe">
                  <meta itemprop="name" content="Quick Oats">
                  <p itemprop="recipeIngredient">1 cup oats</p>
                  <p itemprop="recipeIngredient">2 cups water</p>
                </div>""";
        ParsedRecipe r = parser.parse(html);
        assertThat(r.name()).isEqualTo("Quick Oats");
        assertThat(r.ingredients()).containsExactly("1 cup oats", "2 cups water");
    }

    @Test
    void throwsWithoutARecipeScopeOrIngredients() {
        assertThatThrownBy(() -> parser.parse("<div>just a page</div>"))
                .isInstanceOf(NoRecipeFoundException.class);
        assertThatThrownBy(() -> parser.parse(
                "<div itemscope itemtype=\"http://schema.org/Recipe\"><h1 itemprop=\"name\">Empty</h1></div>"))
                .isInstanceOf(NoRecipeFoundException.class);
    }
}
