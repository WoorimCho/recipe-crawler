package com.example.recipecrawler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code url} is the recipe page to import. {@code creator} is stamped on the
 * created recipe (optional — defaults to {@code import:<source host>}).
 */
public record ImportRequest(
        @NotBlank @Size(max = Limits.URL_LENGTH) String url,
        @Size(max = Limits.CREATOR_LENGTH) String creator) {
}
