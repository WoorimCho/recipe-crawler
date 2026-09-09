package com.example.recipecrawler.parse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls a schema.org {@code Recipe} out of a page's JSON-LD
 * ({@code <script type="application/ld+json">}). Handles the common shapes:
 * a bare Recipe object, an array of objects, or an {@code @graph} wrapper; and
 * instructions given as text, an array of strings, {@code HowToStep}s, or
 * {@code HowToSection}s.
 *
 * <p>Microdata / RDFa are not read — if a page has no JSON-LD Recipe this throws
 * {@link NoRecipeFoundException}.
 */
@Component
public class SchemaOrgRecipeParser {

    private final ObjectMapper mapper;

    public SchemaOrgRecipeParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Result parse(String html) {
        Document doc = Jsoup.parse(html);
        JsonNode recipe = null;
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = mapper.readTree(script.data());
            } catch (RuntimeException badJson) {
                continue;
            }
            recipe = findRecipe(root);
            if (recipe != null) {
                break;
            }
        }
        if (recipe == null) {
            throw new NoRecipeFoundException("no schema.org/Recipe JSON-LD found on the page");
        }

        String name = clean(firstText(recipe.get("name")));
        if (name == null || name.isBlank()) {
            name = "Imported recipe";
        }
        return new Result(name, clean(firstText(recipe.get("recipeYield"))),
                ingredients(recipe), steps(recipe));
    }

    // ── recipe node discovery ────────────────────────────────────────────────

    private JsonNode findRecipe(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode e : node) {
                JsonNode r = findRecipe(e);
                if (r != null) {
                    return r;
                }
            }
            return null;
        }
        if (node.has("@graph")) {
            JsonNode r = findRecipe(node.get("@graph"));
            if (r != null) {
                return r;
            }
        }
        return hasType(node, "Recipe") ? node : null;
    }

    private static boolean hasType(JsonNode node, String wanted) {
        JsonNode type = node.get("@type");
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (wanted.equalsIgnoreCase(t.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return wanted.equalsIgnoreCase(type.asString(""));
    }

    // ── field extraction ─────────────────────────────────────────────────────

    private List<String> ingredients(JsonNode recipe) {
        JsonNode n = recipe.has("recipeIngredient") ? recipe.get("recipeIngredient") : recipe.get("ingredients");
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode e : n) {
                String s = clean(e.asString(""));
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
        } else if (n != null && n.isString()) {
            for (String line : n.asString("").split("\\r?\\n")) {
                String s = clean(line);
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private List<String> steps(JsonNode recipe) {
        List<String> out = new ArrayList<>();
        collectSteps(recipe.get("recipeInstructions"), out);
        if (out.isEmpty()) {
            // some sites put a plain-text method in `description` — leave that alone;
            // a stepless recipe is still valid.
            return out;
        }
        return out;
    }

    private void collectSteps(JsonNode node, List<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isString()) {
            for (String part : node.asString("").split("\\r?\\n")) {
                String s = clean(part);
                if (s != null && s.length() > 1) {
                    out.add(s);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode e : node) {
                collectSteps(e, out);
            }
            return;
        }
        // object: HowToStep {text} / HowToSection {itemListElement} / {name+text}
        if (node.has("itemListElement")) {
            collectSteps(node.get("itemListElement"), out);
            return;
        }
        String text = firstNonBlank(node.get("text"), node.get("name"));
        String s = clean(text);
        if (s != null && s.length() > 1) {
            out.add(s);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String firstText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node.isEmpty() ? null : node.get(0).asString("");
        }
        if (node.isNumber()) {
            return node.asString("");
        }
        return node.asString("");
    }

    private static String firstNonBlank(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && !n.isNull()) {
                String s = n.asString("");
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Decode HTML entities, strip stray tags, collapse whitespace. */
    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = Parser.unescapeEntities(raw, false);
        if (s.contains("<")) {
            s = Jsoup.parse(s).text();
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    public record Result(String name, String yield, List<String> ingredients, List<String> steps) {
    }

    /** No JSON-LD Recipe on the page → 422 to the caller. */
    public static class NoRecipeFoundException extends RuntimeException {
        public NoRecipeFoundException(String message) {
            super(message);
        }
    }
}
