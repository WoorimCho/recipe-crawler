# recipe-crawler — URL → recipe importer

Reads a recipe web page and writes it into the catalogue services. It holds **no
database** — it fetches HTML, parses the embedded schema.org `Recipe` (JSON-LD),
turns each "2 cups flour" line into a structured amount + unit + ingredient, and
calls IngredientCatalogue / RecipeCatalogue to store it.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![Build](https://img.shields.io/badge/build-Maven-blue)
![Port](https://img.shields.io/badge/port-8086-lightgrey)

## What it does

```
        POST /api/import/preview {url}          POST /api/import/commit {url}
                    │                                       │
             fetch HTML (recipe-source CB)          fetch + parse
                    │                                       │
       parse <script type="application/ld+json">    resolve each ingredient by name
                    │                               (create in IngredientCatalogue if new)
       parse "2 cups flour" → 2 / cup / flour               │
                    │                               POST /api/recipes  (RecipeCatalogue)
        match names in IngredientCatalogue                  │
        (no writes)                                  ImportResponse {recipeId, …}
```

Only **JSON-LD** is read (not microdata / RDFa). A page with no
`schema.org/Recipe` block returns **422**; a URL `robots.txt` forbids returns **403**.

## API

| Method & path | Body | Returns |
|---|---|---|
| `POST /api/import/preview` | `{ "url": "https://…" }` | `RecipeDraft` — parsed name / yield / ingredients (each with `matchedId` + `willCreate`) / steps. **Writes nothing.** |
| `POST /api/import/commit`  | `{ "url": "https://…", "creator": "optional" }` | `ImportResponse` — `{ recipeId, name, ingredientCount, stepCount, ingredientsCreated, sourceUrl }` |
| `POST /api/crawl` | `{ "seeds": ["https://…"], "maxPages": 25, "maxDepth": 2, "sameHostOnly": true, "dryRun": false, "creator": "optional" }` | `CrawlReport` — `{ pagesFetched, recipesFound, imported[], skipped[] {url, reason}, stoppedBecause }` |

`creator` defaults to `import:<source host>`. Imported recipes are tagged
`source:imported`.

**Crawl** does a breadth-first walk from the seed URLs. Per host it fetches
`robots.txt` once and honours the `Disallow`/`Allow` rules for our user-agent
(falling back to `*`), and it leaves a politeness gap
(`crawler.politeness-delay`, default 1.5s) between requests to the same host.
Every page carrying a schema.org Recipe is imported; other pages are scanned for
same-host links to follow. Bounded by `maxPages` (1–200) and `maxDepth` (0–5);
each URL is visited once, and a recipe whose `name`+`creator` already exists is
skipped. `dryRun: true` reports what it found and writes nothing.

Ingredient-line parsing handles: decimals, `1 1/2`, unicode fractions (`½`),
ranges (`2-3` → midpoint), unit aliases (`tablespoons` → `tbsp`), parentheticals
and trailing detail (`, sifted`, `to taste`) → the line's note.

## Run

```bash
# whole system (from Projects/)
cd .. && docker compose up --build recipe-crawler

# local — needs IngredientCatalogue :8082 and RecipeCatalogue :8083 up
./mvnw spring-boot:run
```

```bash
curl -s localhost:8086/api/import/preview \
  -H 'content-type: application/json' \
  -d '{"url":"https://www.example.com/recipes/banana-bread"}' | jq
```

## Configuration (env)

| Var | Default |
|---|---|
| `RECIPE_CATALOGUE_URL` | `http://localhost:8083` |
| `INGREDIENT_CATALOGUE_URL` | `http://localhost:8082` |
| `DOWNSTREAM_CONNECT_TIMEOUT` / `DOWNSTREAM_READ_TIMEOUT` | `3s` / `10s` |
| `CRAWLER_MAX_PAGE_BYTES` | `3000000` |
| `CRAWLER_CREATOR_PREFIX` | `import` |
| `CRAWLER_USER_AGENT` | `recipe-crawler/0.1 (+local import tool)` — sent, and matched against robots.txt |
| `CRAWLER_POLITENESS_DELAY` | `1500ms` — min gap between requests to one host |
| `CRAWLER_MAX_PAGES` / `CRAWLER_MAX_DEPTH` | `25` / `2` — crawl defaults |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` |

## Resilience

Each outbound target has a short connect/read timeout (`spring.http.clients.*`)
and its own **circuit breaker** — breaker names `recipe-source`,
`ingredient-catalogue`, `recipe-catalogue` (`config/DownstreamResilience` +
`CircuitBreakerInterceptor`, resilience4j; a per-app copy of the BFF's). A
timeout or a 5xx is a fault; a 4xx isn't. State is on `/actuator/prometheus`
(`resilience4j_circuitbreaker_*`).

## Tests

```bash
./mvnw test     # 33: ingredient-line parsing (10), JSON-LD extraction incl.
                #     @graph / HowToStep / HowToSection / malformed-block (5),
                #     CatalogueGateway contract (4), robots.txt rules (5),
                #     link extraction (3), crawl BFS incl. dedup / robots /
                #     maxPages (5), context load
```

## Status

**v1 complete.** Single-URL `preview`/`commit` plus `POST /api/crawl`
(seed-list, same-host link-following, robots.txt, per-host rate limiting,
visited-set + name/creator dedup). Not built: RDFa/microdata parsing, and a
fallback that hands non-JSON-LD pages to an LLM (see TODO.txt — RAG).

## Security ⚠️

`commit` / `crawl` fetch arbitrary URLs the caller supplies and create catalogue
rows. It's an internal tool on a trusted network — there's no auth, no per-host
allowlist, and SSRF is not mitigated (robots.txt is politeness, not a security
control). Don't expose it publicly. See `../IngredientCatalogue/SECURITY.md`.
