package com.example.recipecrawler.parse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a schema.org {@code Recipe} expressed as HTML <b>microdata</b>
 * ({@code itemscope itemtype=".../Recipe"} with {@code itemprop} attributes) —
 * the format many older / plugin-rendered recipe pages use instead of JSON-LD.
 *
 * <p>One strategy behind {@link RecipeExtractor}. Throws
 * {@link NoRecipeFoundException} when the page has no such block.
 */
@Component
public class MicrodataRecipeParser {

    public ParsedRecipe parse(String html) {
        Document doc = Jsoup.parse(html);
        Element recipe = doc.select("[itemscope][itemtype]").stream()
                .filter(e -> e.attr("itemtype").toLowerCase().matches(".*schema\\.org/recipe/?$"))
                .findFirst()
                .orElseThrow(() -> new NoRecipeFoundException("no schema.org/Recipe microdata on the page"));

        List<String> ingredients = new ArrayList<>();
        for (Element e : ownProps(recipe, "[itemprop=recipeIngredient], [itemprop=ingredients]")) {
            String v = value(e);
            if (!v.isBlank()) {
                ingredients.add(v);
            }
        }
        if (ingredients.isEmpty()) {
            throw new NoRecipeFoundException("Recipe microdata has no recipeIngredient");
        }

        String name = ownProps(recipe, "[itemprop=name]").stream()
                .map(MicrodataRecipeParser::value).filter(s -> !s.isBlank())
                .findFirst().orElse("Imported recipe");
        String yield = ownProps(recipe, "[itemprop=recipeYield]").stream()
                .map(MicrodataRecipeParser::value).filter(s -> !s.isBlank())
                .findFirst().orElse(null);

        return new ParsedRecipe(name, yield, ingredients, steps(recipe));
    }

    private static List<String> steps(Element recipe) {
        List<String> out = new ArrayList<>();
        for (Element container : ownProps(recipe, "[itemprop=recipeInstructions]")) {
            // HowToStep / HowToSection children, then <li>s, then the raw text
            Elements stepEls = container.select("[itemprop=text], [itemprop=itemListElement]");
            if (stepEls.isEmpty()) {
                stepEls = container.select("li");
            }
            if (stepEls.isEmpty()) {
                for (String line : container.wholeText().split("\\r?\\n")) {
                    String s = line.trim().replaceAll("\\s+", " ");
                    if (s.length() > 1) {
                        out.add(s);
                    }
                }
            } else {
                for (Element s : stepEls) {
                    String t = s.text().trim();
                    if (t.length() > 1) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    /** {@code itemprop} elements directly under {@code root} — not inside a nested itemscope. */
    private static Elements ownProps(Element root, String selector) {
        Elements picked = new Elements();
        for (Element e : root.select(selector)) {
            Element scope = e.parents().stream().filter(p -> p.hasAttr("itemscope")).findFirst().orElse(null);
            if (scope == null || scope == root) {
                picked.add(e);
            }
        }
        return picked;
    }

    /** {@code <meta>} carries its value in {@code content}; everything else in its text. */
    private static String value(Element e) {
        String v = e.tagName().equals("meta") ? e.attr("content")
                : e.hasAttr("content") ? e.attr("content")
                : e.text();
        return v == null ? "" : v.trim().replaceAll("\\s+", " ");
    }
}
