package com.example.recipecrawler;

import com.example.recipecrawler.parse.IngredientLineParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IngredientLineParserTest {

    private final IngredientLineParser parser = new IngredientLineParser();

    @Test
    void plainAmountUnitName() {
        IngredientLineParser.Parsed p = parser.parse("2 cups all-purpose flour");
        assertThat(p.amount()).isEqualTo(2.0);
        assertThat(p.unit()).isEqualTo("cup");
        assertThat(p.name()).isEqualTo("all-purpose flour");
        assertThat(p.note()).isNull();
    }

    @Test
    void mixedNumberAndFractionGlyph() {
        assertThat(parser.parse("1 1/2 tsp vanilla extract").amount()).isEqualTo(1.5);
        assertThat(parser.parse("1 ½ tsp vanilla extract").amount()).isEqualTo(1.5);
        assertThat(parser.parse("¾ cup sugar").amount()).isEqualTo(0.75);
    }

    @Test
    void unitAliasesNormaliseToRecipeCatalogueTokens() {
        assertThat(parser.parse("3 tablespoons olive oil").unit()).isEqualTo("tbsp");
        assertThat(parser.parse("200 grams butter").unit()).isEqualTo("g");
        assertThat(parser.parse("1 pound ground beef").unit()).isEqualTo("lb");
    }

    @Test
    void trailingDetailBecomesANote() {
        IngredientLineParser.Parsed p = parser.parse("1 cup walnuts, toasted and chopped");
        assertThat(p.name()).isEqualTo("walnuts");
        assertThat(p.note()).isEqualTo("toasted and chopped");
    }

    @Test
    void parentheticalBecomesANote() {
        IngredientLineParser.Parsed p = parser.parse("2 cups (250 g) bread flour");
        assertThat(p.amount()).isEqualTo(2.0);
        assertThat(p.unit()).isEqualTo("cup");
        assertThat(p.name()).isEqualTo("bread flour");
        assertThat(p.note()).contains("250 g");
    }

    @Test
    void noAmountNoUnit() {
        IngredientLineParser.Parsed p = parser.parse("Salt and pepper to taste");
        assertThat(p.amount()).isNull();
        assertThat(p.unit()).isNull();
        assertThat(p.name()).isEqualTo("salt and pepper");
        assertThat(p.note()).isEqualTo("to taste");
    }

    @Test
    void countyLine() {
        IngredientLineParser.Parsed p = parser.parse("3 large eggs");
        assertThat(p.amount()).isEqualTo(3.0);
        assertThat(p.unit()).isNull();
        assertThat(p.name()).isEqualTo("large eggs");
    }

    @Test
    void aPinchOf() {
        IngredientLineParser.Parsed p = parser.parse("a pinch of nutmeg");
        assertThat(p.amount()).isEqualTo(1.0);
        assertThat(p.unit()).isEqualTo("pinch");
        assertThat(p.name()).isEqualTo("nutmeg");
    }

    @Test
    void rangeTakesTheMidpoint() {
        assertThat(parser.parse("2-3 cloves garlic, minced").amount()).isEqualTo(2.5, within(1e-9));
        assertThat(parser.parse("2-3 cloves garlic, minced").unit()).isEqualTo("clove");
    }

    @Test
    void emptyIsSafe() {
        IngredientLineParser.Parsed p = parser.parse("   ");
        assertThat(p.name()).isEmpty();
        assertThat(p.amount()).isNull();
    }
}
