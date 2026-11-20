package com.example.recipecrawler.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads a recipe off a page, trying each strategy in turn:
 *
 * <ol>
 *   <li>{@link SchemaOrgRecipeParser} — schema.org JSON-LD.</li>
 *   <li>{@link MicrodataRecipeParser} — schema.org HTML microdata.</li>
 *   <li>{@link LlmRecipeParser} — free-text fallback via an LLM.
 *       {@link #extractWithLlmFallback} only, and only when an LLM is configured;
 *       {@link #extract} (what the crawler uses) stops after microdata.</li>
 * </ol>
 *
 * If every eligible strategy comes up empty, {@link NoRecipeFoundException}
 * propagates (→ 422). RDFa is still out of scope.
 */
@Component
public class RecipeExtractor {

    private static final Logger log = LoggerFactory.getLogger(RecipeExtractor.class);

    private final SchemaOrgRecipeParser jsonLd;
    private final MicrodataRecipeParser microdata;
    private final LlmRecipeParser llm;

    public RecipeExtractor(SchemaOrgRecipeParser jsonLd, MicrodataRecipeParser microdata, LlmRecipeParser llm) {
        this.jsonLd = jsonLd;
        this.microdata = microdata;
        this.llm = llm;
    }

    /** schema.org strategies only — what the crawler runs on every page it walks. */
    public ParsedRecipe extract(String html) {
        try {
            ParsedRecipe r = jsonLd.parse(html);
            log.debug("recipe read from JSON-LD: {}", r.name());
            return r;
        } catch (NoRecipeFoundException noJsonLd) {
            ParsedRecipe r = microdata.parse(html);   // its own NoRecipeFoundException propagates
            log.debug("recipe read from microdata: {}", r.name());
            return r;
        }
    }

    /**
     * {@link #extract} plus, when that finds nothing <em>and</em> an LLM is
     * configured, the LLM free-text fallback. Used by the single-URL import path;
     * the crawler stays on {@link #extract} so a walk never fans out into an LLM
     * call per page. With no LLM configured this is exactly {@link #extract} —
     * the schema.org "no recipe" result is re-thrown unchanged.
     */
    public ParsedRecipe extractWithLlmFallback(String html) {
        try {
            return extract(html);
        } catch (NoRecipeFoundException noSchemaOrg) {
            if (!llm.isConfigured()) {
                throw noSchemaOrg;
            }
            ParsedRecipe r = llm.parse(html);
            log.debug("recipe read via LLM fallback: {}", r.name());
            return r;
        }
    }
}
