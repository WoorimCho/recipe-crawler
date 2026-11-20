package com.example.recipecrawler.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The free-text fallback: when neither {@link SchemaOrgRecipeParser} nor
 * {@link MicrodataRecipeParser} finds a machine-readable recipe, send the page's
 * visible text to an LLM ({@link LlmClient}) and ask for the same
 * {@code {name, yield, ingredients[], steps[]}} shape the other strategies
 * return. The ingredient lines still flow through {@link IngredientLineParser}
 * downstream, so the model only has to give each one as a cook would read it.
 *
 * <p>Import path only (single-URL preview / commit), and only when
 * {@code llm.base-url} + {@code llm.model} + {@code llm.api-key} are all set —
 * otherwise this throws {@link NoRecipeFoundException} like the other strategies
 * and the caller gets the usual 422. A transport failure or open breaker
 * propagates (→ 502); a bad-JSON or no-recipe answer is a 422.
 */
@Component
public class LlmRecipeParser {

    private static final Logger log = LoggerFactory.getLogger(LlmRecipeParser.class);

    private static final String SYSTEM = """
            You extract a single recipe from the visible text of a web page.
            Reply with ONE JSON object and nothing else:
              {"name": string|null, "yield": string|null,
               "ingredients": [string], "steps": [string]}
            Each ingredient is one line exactly as a cook would read it, e.g.
            "2 cups all-purpose flour, sifted". Each step is one instruction, in
            order, with no leading number. If the text is not a recipe, reply
            {"name": null, "yield": null, "ingredients": [], "steps": []}.
            No markdown, no code fences, no commentary.""";

    private final LlmClient llm;
    private final ObjectMapper mapper;
    private final int maxInputChars;

    public LlmRecipeParser(LlmClient llm, ObjectMapper mapper,
                           @Value("${llm.max-input-chars:12000}") int maxInputChars) {
        this.llm = llm;
        this.mapper = mapper;
        this.maxInputChars = maxInputChars;
    }

    /** True when an LLM endpoint is configured — {@link RecipeExtractor} skips this strategy otherwise. */
    public boolean isConfigured() {
        return llm.isConfigured();
    }

    public ParsedRecipe parse(String html) {
        if (!llm.isConfigured()) {
            throw new NoRecipeFoundException(
                    "no LLM configured for the free-text fallback (set LLM_BASE_URL, LLM_MODEL, LLM_API_KEY)");
        }
        String text = visibleText(html);
        if (text.length() < 200) {
            throw new NoRecipeFoundException("the page has too little text for the LLM fallback");
        }

        String reply;
        try {
            reply = llm.complete(SYSTEM, "PAGE TEXT:\n\n" + text);
        } catch (RestClientResponseException http) {
            log.warn("LLM fallback call failed: HTTP {} — {}", http.getStatusCode().value(),
                    truncate(http.getResponseBodyAsString(), 200));
            throw new NoRecipeFoundException(
                    "the LLM fallback call failed (HTTP " + http.getStatusCode().value() + ")");
        }
        // a ResourceAccessException (unreachable / breaker open) propagates → 502

        LlmRecipe parsed = readJson(reply);
        String name = parsed.name() == null ? "" : parsed.name().trim();
        List<String> ingredients = clean(parsed.ingredients());
        if (name.isBlank() || ingredients.isEmpty()) {
            throw new NoRecipeFoundException("the LLM could not find a recipe on this page");
        }
        List<String> steps = clean(parsed.steps());
        log.debug("LLM fallback extracted \"{}\" ({} ingredients, {} steps)", name, ingredients.size(), steps.size());
        String yield = parsed.yield() == null || parsed.yield().isBlank() ? null : parsed.yield().trim();
        return new ParsedRecipe(name, yield, ingredients, steps);
    }

    private String visibleText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, nav, footer, header, form, aside, template, svg").remove();
        String text = (doc.body() != null ? doc.body().text() : doc.text()).replaceAll("\\s+", " ").trim();
        return text.length() > maxInputChars ? text.substring(0, maxInputChars) : text;
    }

    private LlmRecipe readJson(String reply) {
        try {
            return mapper.readValue(extractJsonObject(reply), LlmRecipe.class);
        } catch (RuntimeException bad) {
            log.warn("LLM fallback returned unparseable JSON: {}", truncate(reply, 300));
            throw new NoRecipeFoundException(
                    "the LLM fallback returned a response that wasn't valid recipe JSON");
        }
    }

    /** Some models wrap the object in prose or ``` fences — take the outermost {@code {...}}. */
    static String extractJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return start >= 0 && end > start ? s.substring(start, end + 1) : s;
    }

    private static List<String> clean(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) {
            for (String v : in) {
                if (v != null && !v.isBlank()) {
                    out.add(v.replaceAll("\\s+", " ").trim());
                }
            }
        }
        return out;
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmRecipe(String name, String yield, List<String> ingredients, List<String> steps) {
    }
}
