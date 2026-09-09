package com.example.recipecrawler.parse;

import java.util.List;

/**
 * A recipe pulled off a page by any strategy (JSON-LD, microdata, …), before
 * ingredient lines are parsed or anything is resolved against the catalogues.
 */
public record ParsedRecipe(String name, String yield, List<String> ingredients, List<String> steps) {
}
