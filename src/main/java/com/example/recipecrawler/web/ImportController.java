package com.example.recipecrawler.web;

import com.example.recipecrawler.dto.ImportRequest;
import com.example.recipecrawler.dto.ImportResponse;
import com.example.recipecrawler.dto.RecipeDraft;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <ul>
 *   <li>{@code POST /api/import/preview} — fetch + parse a recipe URL, return the
 *       draft (ingredients parsed, matched against IngredientCatalogue). Writes
 *       nothing.</li>
 *   <li>{@code POST /api/import/commit} — the same, then create any missing
 *       ingredients and the recipe in the catalogue services.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/preview")
    public RecipeDraft preview(@Valid @RequestBody ImportRequest request) {
        return imports.preview(request.url());
    }

    @PostMapping("/commit")
    public ImportResponse commit(@Valid @RequestBody ImportRequest request) {
        return imports.commit(request.url(), request.creator());
    }
}
