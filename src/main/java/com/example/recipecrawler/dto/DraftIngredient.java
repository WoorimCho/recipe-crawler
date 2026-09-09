package com.example.recipecrawler.dto;

/**
 * One parsed "2 cups flour" line.
 *
 * @param raw          the original text from the page
 * @param amount       numeric quantity, or null if the line had none
 * @param unit         unit token RecipeCatalogue understands (g, cup, tbsp, …), or null
 * @param name         the ingredient name, lower-cased
 * @param note         trailing detail ("sifted", "to taste", "room temperature"), or null
 * @param matchedId    id of the existing IngredientCatalogue entry, or null if none matched
 * @param willCreate   true when committing this line would create a new ingredient
 */
public record DraftIngredient(
        String raw, Double amount, String unit, String name, String note,
        Long matchedId, boolean willCreate) {
}
