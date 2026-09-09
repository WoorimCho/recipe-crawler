package com.example.recipecrawler.parse;

/** No strategy could read a recipe from the page → 422 to the caller. */
public class NoRecipeFoundException extends RuntimeException {
    public NoRecipeFoundException(String message) {
        super(message);
    }
}
