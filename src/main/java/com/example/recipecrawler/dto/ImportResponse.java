package com.example.recipecrawler.dto;

/** Result of a successful {@code commit}. */
public record ImportResponse(
        long recipeId,
        String name,
        int ingredientCount,
        int stepCount,
        int ingredientsCreated,
        String sourceUrl) {
}
