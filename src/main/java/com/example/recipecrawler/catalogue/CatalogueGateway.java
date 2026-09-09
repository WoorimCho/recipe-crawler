package com.example.recipecrawler.catalogue;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The importer's calls into the two catalogue services: look up / create
 * ingredients, and create the recipe. Both clients carry a circuit breaker.
 */
@Component
public class CatalogueGateway {

    private final RestClient ingredients;
    private final RestClient recipes;

    public CatalogueGateway(RestClient ingredientCatalogueClient, RestClient recipeCatalogueClient) {
        this.ingredients = ingredientCatalogueClient;
        this.recipes = recipeCatalogueClient;
    }

    /** Id of the ingredient whose name equals {@code name} (case-insensitive), or null. */
    public Long findIngredientId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        JsonNode page = ingredients.get()
                .uri(uri -> uri.path("/api/ingredients").queryParam("name", name).queryParam("size", 25).build())
                .retrieve()
                .body(JsonNode.class);
        JsonNode content = page == null ? null : page.get("content");
        if (content == null || !content.isArray()) {
            return null;
        }
        String target = name.trim().toLowerCase(Locale.ROOT);
        for (JsonNode e : content) {
            if (target.equals(e.path("name").asString("").trim().toLowerCase(Locale.ROOT))) {
                return e.path("id").asLong();
            }
        }
        return null;
    }

    /** Find the ingredient by name, creating it if it doesn't exist. */
    public Resolved resolveIngredientId(String name) {
        Long existing = findIngredientId(name);
        if (existing != null) {
            return new Resolved(existing, false);
        }
        JsonNode created = ingredients.post()
                .uri("/api/ingredients")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .retrieve()
                .body(JsonNode.class);
        return new Resolved(created.path("id").asLong(), true);
    }

    /** Create the recipe; returns its new id. */
    public long createRecipe(String name, String creator, List<Line> lines, List<String> steps) {
        List<Map<String, Object>> ingredientBodies = new ArrayList<>();
        for (Line l : lines) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ingredientId", l.ingredientId());
            body.put("quantity", l.quantity());
            if (l.amount() != null) {
                body.put("amount", l.amount());
                body.put("unit", l.unit());       // RecipeCatalogue rejects an amount with no unit
            }
            ingredientBodies.add(body);
        }
        List<Map<String, Object>> stepBodies = new ArrayList<>();
        int pos = 1;
        for (String s : steps) {
            stepBodies.add(Map.of("position", pos++, "text", s));
        }

        Map<String, Object> recipe = new LinkedHashMap<>();
        recipe.put("name", name);
        recipe.put("creator", creator);
        recipe.put("steps", stepBodies);
        recipe.put("ingredients", ingredientBodies);
        recipe.put("tags", List.of("source:imported"));

        JsonNode created = recipes.post()
                .uri("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(recipe)
                .retrieve()
                .body(JsonNode.class);
        return created.path("id").asLong();
    }

    public record Resolved(long ingredientId, boolean created) {
    }

    /** A resolved recipe line ready to send to RecipeCatalogue. amount/unit may be null. */
    public record Line(long ingredientId, String quantity, Double amount, String unit) {
    }
}
