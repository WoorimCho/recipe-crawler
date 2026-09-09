package com.example.recipecrawler.web;

import com.example.recipecrawler.catalogue.CatalogueGateway;
import com.example.recipecrawler.dto.DraftIngredient;
import com.example.recipecrawler.dto.ImportResponse;
import com.example.recipecrawler.dto.RecipeDraft;
import com.example.recipecrawler.fetch.RecipePageFetcher;
import com.example.recipecrawler.parse.IngredientLineParser;
import com.example.recipecrawler.parse.SchemaOrgRecipeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Ties fetch → parse → resolve → create together. */
@Service
public class ImportService {

    private final RecipePageFetcher fetcher;
    private final SchemaOrgRecipeParser recipeParser;
    private final IngredientLineParser lineParser;
    private final CatalogueGateway catalogue;
    private final String creatorPrefix;

    public ImportService(RecipePageFetcher fetcher, SchemaOrgRecipeParser recipeParser,
                         IngredientLineParser lineParser, CatalogueGateway catalogue,
                         @Value("${crawler.creator-prefix:import}") String creatorPrefix) {
        this.fetcher = fetcher;
        this.recipeParser = recipeParser;
        this.lineParser = lineParser;
        this.catalogue = catalogue;
        this.creatorPrefix = creatorPrefix;
    }

    /** Fetch + parse only — nothing is written; each line says whether committing would create it. */
    public RecipeDraft preview(String url) {
        SchemaOrgRecipeParser.Result r = recipeParser.parse(fetcher.fetch(url));
        List<DraftIngredient> lines = new ArrayList<>();
        for (String raw : r.ingredients()) {
            IngredientLineParser.Parsed p = lineParser.parse(raw);
            Long matched = p.name().isBlank() ? null : catalogue.findIngredientId(p.name());
            lines.add(new DraftIngredient(p.raw(), p.amount(), p.unit(), p.name(), p.note(),
                    matched, matched == null && !p.name().isBlank()));
        }
        return new RecipeDraft(url, r.name(), r.yield(), lines, r.steps());
    }

    /** Fetch + parse, create any missing ingredients, then create the recipe. */
    public ImportResponse commit(String url, String creatorOverride) {
        SchemaOrgRecipeParser.Result r = recipeParser.parse(fetcher.fetch(url));

        List<CatalogueGateway.Line> resolved = new ArrayList<>();
        int created = 0;
        for (String raw : r.ingredients()) {
            IngredientLineParser.Parsed p = lineParser.parse(raw);
            if (p.name().isBlank()) {
                continue;                     // nothing to hang a line on
            }
            CatalogueGateway.Resolved res = catalogue.resolveIngredientId(p.name());
            if (res.created()) {
                created++;
            }
            resolved.add(new CatalogueGateway.Line(res.ingredientId(), p.raw(), p.amount(), p.unit()));
        }

        String creator = creator(url, creatorOverride);
        long recipeId = catalogue.createRecipe(r.name(), creator, resolved, r.steps());
        return new ImportResponse(recipeId, r.name(), resolved.size(), r.steps().size(), created, url);
    }

    private String creator(String url, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? creatorPrefix : creatorPrefix + ":" + host.replaceFirst("^www\\.", "");
        } catch (RuntimeException e) {
            return creatorPrefix;
        }
    }
}
