package com.example.recipecrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Recipe crawler / importer.
 *
 * <p>Given a recipe page URL it fetches the HTML, reads the embedded schema.org
 * {@code Recipe} (JSON-LD), turns each "2 cups flour" line into a structured
 * amount + unit + ingredient, resolves those against IngredientCatalogue
 * (creating any that are new), and writes the recipe to RecipeCatalogue.
 *
 * <p>It holds no database of its own — it is a thin translation layer in front
 * of the two catalogue services.
 */
@SpringBootApplication
public class RecipeCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipeCrawlerApplication.class, args);
    }
}
