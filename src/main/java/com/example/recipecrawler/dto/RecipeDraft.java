package com.example.recipecrawler.dto;

import java.util.List;

/**
 * What we read from the page, before anything is written. {@code preview} returns
 * this; {@code commit} builds one internally and then creates it.
 */
public record RecipeDraft(
        String sourceUrl,
        String name,
        String yield,
        List<DraftIngredient> ingredients,
        List<String> steps) {
}
