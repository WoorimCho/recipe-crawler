# recipe-crawler — URL → recipe importer

Reads a recipe web page and writes it into the catalogue services. It holds **no
database** — it fetches HTML, reads the embedded schema.org `Recipe`
(JSON-LD, then HTML microdata), turns each "2 cups flour" line into a structured
amount + unit + ingredient, and calls IngredientCatalogue / RecipeCatalogue to
store it. The schema.org path uses no LLM; an **optional LLM fallback** (below)
covers free-text pages on single-URL import, and is off unless configured.

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

A recipe is read by trying, in order: **schema.org JSON-LD**
(`<script type="application/ld+json">`), then **schema.org HTML microdata**
(`itemscope itemtype=".../Recipe"` + `itemprop`), then — on single-URL import
only, and only when configured — the **LLM free-text fallback** (below). RDFa is
still out of scope. When nothing matches, a page returns **422**.

A URL `robots.txt` forbids returns **403**. If the source site itself blocks
the fetch — bot protection or a login wall (HTTP 401/403/429/451) — that is
**422** with a "the site blocked automated access" message (retrying won't
help; add the recipe by hand). A missing page is **404**; any other upstream
error is **502**.

### Free-text fallback (LLM)

When a **single-URL import** (`POST /api/import/preview` | `/commit`) hits a
page with no schema.org JSON-LD or microdata, the page's visible text is POSTed
to an **OpenAI-compatible `/chat/completions`** endpoint, which returns the
recipe as `{name, yield, ingredients[], steps[]}` — the same shape the other
strategies produce, so the ingredient lines still flow through the normal
`"2 cups flour" → 2 / cup / flour` parser.

- **Provider-neutral.** Point `LLM_BASE_URL` at OpenAI, Anthropic
  (`https://api.anthropic.com/v1`), Gemini's OpenAI endpoint, Groq, OpenRouter,
  a local Ollama (`http://localhost:11434/v1`), etc. No vendor SDK.
- **Off by default.** Skipped unless `LLM_BASE_URL` + `LLM_MODEL` + `LLM_API_KEY`
  are all set (for a keyless local Ollama, set `LLM_API_KEY` to any non-blank
  string — it's ignored).
- **Import only.** `POST /api/crawl` never calls it, so a walk can't fan out
  into one LLM call per page.
- A bad-JSON or "not a recipe" answer is a **422**; the endpoint being
  unreachable / its breaker open is a **502**.

## API

| Method & path | Body | Returns |
|---|---|---|
| `POST /api/import/preview` | `{ "url": "https://…" }` | `RecipeDraft` — parsed name / yield / ingredients (each with `matchedId` + `willCreate`) / steps. **Writes nothing.** |
| `POST /api/import/commit`  | `{ "url": "https://…", "creator": "optional" }` | `ImportResponse` — `{ recipeId, name, ingredientCount, stepCount, ingredientsCreated, sourceUrl }` |
| `POST /api/crawl` | `{ "seeds": ["https://…"], "maxPages": 25, "maxDepth": 2, "sameHostOnly": true, "dryRun": false, "creator": "optional" }` | `CrawlReport` — `{ pagesFetched, recipesFound, imported[], skipped[] {url, reason}, stoppedBecause }` |

`creator` defaults to `import:<source host>`. Imported recipes are tagged
`source:imported`.

On failure every endpoint returns an RFC 9457 `application/problem+json` body
whose `detail` is a plain-language sentence — 422 for an unparseable or
site-blocked page, 404 for a missing one, 502 for an upstream failure. `crawl`
never fails on one bad page: it lands in `skipped[]` with that sentence as
`reason`.

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
# once: the HMAC secret lives in a git-ignored .env, never in the repo
cp .env.example .env      # fill in INTERNAL_AUTH_SECRET (openssl rand -hex 24) - the same value in every service

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
| `INTERNAL_AUTH_SECRET` | **none** — the HMAC secret every service shares (at least 32 characters), from `.env`; startup fails without it (finding N1) |
| `RECIPE_CATALOGUE_URL` | `http://localhost:8083` |
| `INGREDIENT_CATALOGUE_URL` | `http://localhost:8082` |
| `DOWNSTREAM_CONNECT_TIMEOUT` / `DOWNSTREAM_READ_TIMEOUT` | `3s` / `10s` |
| `CRAWLER_MAX_PAGE_BYTES` | `3000000` |
| `CRAWLER_CREATOR_PREFIX` | `import` |
| `CRAWLER_USER_AGENT` | `Mozilla/5.0 (compatible; RecipeCrawler/0.1; +…)` — sent as `User-Agent`; the `RecipeCrawler` token is what's matched against robots.txt groups |
| `CRAWLER_POLITENESS_DELAY` | `1500ms` — min gap between requests to one host |
| `CRAWLER_MAX_PAGES` / `CRAWLER_MAX_DEPTH` | `25` / `2` — crawl defaults |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` |
| `LLM_BASE_URL` / `LLM_MODEL` / `LLM_API_KEY` | *(unset)* — set all three to enable the free-text fallback; any OpenAI-compatible endpoint |
| `LLM_JSON_MODE` | `true` — send `response_format: {type: json_object}` (turn off for endpoints that reject it) |
| `LLM_MAX_INPUT_CHARS` / `LLM_MAX_OUTPUT_TOKENS` | `12000` / `2048` |
| `LLM_TEMPERATURE` / `LLM_READ_TIMEOUT_SECONDS` | `0` / `45` |

## Resilience

Each outbound target has a short connect/read timeout (`spring.http.clients.*`)
and its own **circuit breaker** — breaker names `recipe-source`,
`ingredient-catalogue`, `recipe-catalogue`, and `llm` when the fallback is
configured (`config/DownstreamResilience` + `CircuitBreakerInterceptor`,
resilience4j; a per-app copy of the BFF's). A timeout or a 5xx is a fault; a 4xx
isn't. State is on `/actuator/prometheus` (`resilience4j_circuitbreaker_*`). The
`llm` client gets a longer read timeout (`LLM_READ_TIMEOUT_SECONDS`, 45s).

## Tests

```bash
./mvnw test     # 69: ingredient-line parsing (10), JSON-LD extraction (5),
                #     microdata extraction (3), JSON-LD→microdata→LLM fallback (6),
                #     LLM free-text parser (10), OpenAI-compatible client (5),
                #     CatalogueGateway contract (4), robots.txt rules (5),
                #     link extraction (3), crawl BFS (6), request limits (5),
                #     shared-secret check (6), context load
```

## Status

**v1 complete.** Single-URL `preview`/`commit` plus `POST /api/crawl`
(seed-list, same-host link-following, robots.txt, per-host rate limiting,
visited-set + name/creator dedup); recipes read from JSON-LD or HTML microdata,
with an optional provider-neutral **LLM free-text fallback** on the import path
(off unless configured). Not built: RDFa parsing.

## Security ⚠️

`commit` / `crawl` fetch arbitrary URLs the caller supplies and create catalogue
rows. Every `/api/**` call needs a valid HMAC `X-Internal-Auth` header (finding
C2; the shared secret has **no default** since N1, fixed 2026-11-02) — but
there's no per-host allowlist, and **SSRF is not mitigated** (finding N2: nothing
stops a fetch of `localhost`, a private address or a cloud metadata endpoint;
robots.txt is politeness, not a security control). Don't expose it, and keep it
out of any public deploy until N2 is fixed. The UI's import / crawl routes now
need a login (N7), but any registered user can use them. See
`../IngredientCatalogue/SECURITY.md`.

**Limits** (finding N4; `dto/Limits`): ≤ 20 crawl seeds, URLs ≤ 2 048 chars,
`creator` ≤ 255; the crawl itself is bounded by `maxPages` (≤ 200), `maxDepth`
(≤ 5) and the page-size cap.

When the **LLM fallback** is configured, the visible text of an imported page
(capped at `LLM_MAX_INPUT_CHARS`) is sent to whatever `LLM_BASE_URL` points at —
a third party unless that's a local model. `LLM_API_KEY` is a secret; supply it
via the environment, not the image. The fallback only fires for pages the
schema.org parsers can't read, and never during a crawl.
