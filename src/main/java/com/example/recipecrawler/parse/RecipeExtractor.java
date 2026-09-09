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
 * </ol>
 *
 * No LLM. If every strategy comes up empty, {@link NoRecipeFoundException}
 * propagates (→ 422). RDFa and free-text pages are still out of scope.
 */
@Component
public class RecipeExtractor {

    private static final Logger log = LoggerFactory.getLogger(RecipeExtractor.class);

    private final SchemaOrgRecipeParser jsonLd;
    private final MicrodataRecipeParser microdata;

    public RecipeExtractor(SchemaOrgRecipeParser jsonLd, MicrodataRecipeParser microdata) {
        this.jsonLd = jsonLd;
        this.microdata = microdata;
    }

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
}
