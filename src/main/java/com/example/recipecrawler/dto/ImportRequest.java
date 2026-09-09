package com.example.recipecrawler.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code url} is the recipe page to import. {@code creator} is stamped on the
 * created recipe (optional — defaults to {@code import:<source host>}).
 */
public record ImportRequest(@NotBlank String url, String creator) {
}
