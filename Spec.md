# LabelLens — Specification

**Status:** v1 draft · **Author:** Oussama Zaam · **Date:** 2026-10-10

## 1. Problem

Packaged food labels are hard to read and harder to compare. Nutrition tables are printed small, on curved or shiny packaging, often in French and Arabic. Ingredient lists hide allergens and additives under technical names.

LabelLens takes photos of a food package and returns:
1. the nutrition table and ingredients, as structured data,
2. the Nutri-Score grade (A–E), computed with the official formula,
3. the allergens and additives found in the ingredients,
4. a short, sourced explanation of each additive.

**The real point of the project is section 8: an evaluation harness that measures every AI step and blocks regressions in CI.** The harness is generic. Food labels are the first domain plugged into it.

## 2. Goals and non-goals

**Goals (v1)**
- Extract label data from photos and measure how accurately it does so.
- Compute Nutri-Score deterministically, and verify the formula against thousands of reference products.
- Tag allergens and additives with a fine-tuned small model, and compare it honestly against a large prompted model.
- Explain additives with RAG, citing the source entry, and measure whether explanations stay grounded.
- Make every LLM call observable: tokens, cost, latency, model.
- Deploy with a public demo and CI/CD, reproducible locally with `docker compose up`.

**Non-goals (v1)**
- Barcode scanning or product lookup. Photos are the input; that's what makes extraction worth measuring.
- Dietary advice, health recommendations, or personalized nutrition. The app reports label information only.
- User accounts. A single API key protects the API.
- Mobile app. The web UI works on a phone browser, which is enough to photograph a product.
- Nutri-Score for beverages, cheese, and fats/oils/nuts. These have special rules; v1 marks them `NOT_SCORED`. Adding them is listed in v2 ideas.

## 3. Core flow

1. User uploads 2–3 photos of a product: front (optional), nutrition table, ingredients list.
2. **Extraction** (vision LLM, structured output) → `LabelData`: product name, brand, per-100g nutrients, ingredients text, detected language, and a declared category (general food / beverage / cheese / fat).
3. **Scoring** (Java) → Nutri-Score points breakdown and grade, or `NOT_SCORED` with a reason.
4. **Tagging** (fine-tuned small model) → allergens (EU list of 14) and additives (E-numbers).
5. **Explanation** (RAG) → for each additive, a 2-sentence explanation grounded in the additives database, with the entry ID cited.
6. UI shows all of it, with the points breakdown, so the grade is never a black box.

## 4. Key design decision: which step uses what

| Step | Handled by | Why |
|---|---|---|
| Reading the label | Vision LLM + JSON schema validation | Photos of real packaging are where a vision model earns its cost. |
| Nutri-Score | **Plain Java** | It's a published formula. An LLM doing this arithmetic would be a liability: untestable and non-deterministic. |
| Allergen and additive tagging | **Fine-tuned small model** | Narrow, high-volume classification with thousands of free labelled examples. A candidate for fine-tuning, if the eval says so. |
| Additive explanations | Chat model + RAG over the additives database | Needs fluent text, but must not invent facts. Retrieval supplies the facts; eval checks the model sticks to them. |
| Unit conversion, validation (e.g. sugars ≤ carbohydrates, salt = sodium × 2.5) | **Plain Java** | Sanity checks that catch extraction errors before they reach the score. |

Rule of thumb applied throughout: **anything with a formula or a threshold is code; the model only reads, classifies, and explains.**

## 5. Architecture

```
            ┌──────────────────────── Spring Boot app ─────────────────────────┐
 Photos ──▶ │ ProductController                                                │
 (htmx UI   │   ├─▶ StorageService ─────────────────────▶ Azure Blob Storage   │
  or REST)  │   ├─▶ ExtractionService ── Spring AI ─────▶ Azure OpenAI (vision)│
            │   │     └ schema validation + sanity checks + 1 retry            │
            │   ├─▶ NutriScoreCalculator (pure Java, no I/O)                   │
            │   ├─▶ TaggingService ─────────────────────▶ fine-tuned model     │
            │   └─▶ ExplanationService ─ AdditiveRetriever ─▶ pgvector         │
            │                          └─────────────────▶ Azure OpenAI (chat) │
            │ LlmCallRecorder (tokens, cost, latency) ─▶ Postgres + App Insights│
            └──────────────────────────────────────────────────────────────────┘

 eval-core/   (generic harness, no food-specific code)  ◀── eval-food/ (datasets + scorers wiring)
 training/    (Python: data prep + fine-tune job)
```

**Repository layout**

```
labellens/
  app/          Spring Boot service
  eval-core/    Generic eval harness (Java library)
  eval-food/    Food datasets, case definitions, baseline.json
  training/     Python: Open Food Facts prep, fine-tune launch, data cards
  docs/         SPEC.md, DATA_PLAN.md, decisions/
```

## 6. Data model

```
product
  id                UUID PK
  created_at        TIMESTAMPTZ
  status            TEXT        -- PENDING | EXTRACTED | DONE | FAILED
  photo_urls        TEXT[]

label_extraction
  id                UUID PK
  product_id        UUID FK
  name, brand       TEXT
  category          TEXT        -- GENERAL | BEVERAGE | CHEESE | FAT
  energy_kj_100g    NUMERIC(8,2)
  sugars_100g       NUMERIC(6,2)
  sat_fat_100g      NUMERIC(6,2)
  salt_100g         NUMERIC(6,3)
  fibre_100g        NUMERIC(6,2)
  protein_100g      NUMERIC(6,2)
  fvl_percent       NUMERIC(5,2) NULL   -- fruit/vegetables/legumes, often absent from labels
  ingredients_text  TEXT
  language          TEXT        -- fr | ar | en | mixed
  sanity_warnings   JSONB
  model             TEXT

nutri_score
  product_id        UUID FK
  grade             CHAR(1) NULL
  status            TEXT        -- SCORED | NOT_SCORED
  points_breakdown  JSONB       -- every component, so the UI can explain the grade
  algorithm_version TEXT

tag
  product_id        UUID FK
  kind              TEXT        -- ALLERGEN | ADDITIVE
  code              TEXT        -- e.g. "en:gluten", "en:e330"
  confidence        NUMERIC(3,2)
  model             TEXT

additive_entry
  id                TEXT PK     -- e.g. "en:e330"
  name              TEXT
  function_class    TEXT
  content           TEXT
  embedding         VECTOR(1536)

llm_call
  id, product_id, purpose (EXTRACTION | TAGGING | EXPLANATION | EMBEDDING | JUDGE),
  model, input_tokens, output_tokens, cost_usd, latency_ms, success, created_at
```

Migrations managed with Flyway.

## 7. API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/products` | Multipart upload of 1–3 photos. Returns the product with all results. Synchronous in v1. |
| `GET` | `/api/products/{id}` | Full result. |
| `GET` | `/api/products` | Paginated list. |
| `GET` | `/api/additives/{id}` | Additive entry, for citation links. |
| `GET` | `/api/metrics/llm` | Cost and latency aggregated by purpose and model. |
| `GET` | `/api/eval/latest` | The latest committed eval report, rendered in the UI. |
| `GET` | `/actuator/health` | Health check. |

Errors use RFC 9457 problem details. Synchronous processing is a deliberate v1 simplification; the README says so.

## 8. Evaluation (centrepiece)

### 8.1 The generic harness (`eval-core`)

Domain-agnostic building blocks, so the same harness can evaluate any extract → compute → classify → explain pipeline:

| Concept | What it is |
|---|---|
| `Dataset<I, E>` | A versioned list of cases: an input `I` and an expected output `E`, with tags for breakdowns (e.g. `lang=ar`, `photo=blurry`). |
| `Pipeline<I, O>` | Anything under test: one model call, one service, or the whole flow. |
| `Scorer<E, O>` | Compares expected vs actual. Built-ins: `ExactMatch`, `NumericTolerance`, `TextErrorRate`, `SetPrecisionRecall` (with per-label results), `ValidatedLlmJudge`. |
| `Report` | Metrics overall and per tag, plus cost and latency per case. Written as JSON and Markdown. |
| `Gate` | Compares a report against `baseline.json` and fails the build if a headline metric drops beyond a threshold. |

Food-specific code lives only in `eval-food`. Adding a new domain means a new dataset and a mapping, not a new harness.

### 8.2 What is evaluated, and how

| # | Step | Test set | Metrics | Notes |
|---|---|---|---|---|
| E1 | **Extraction** | 40 products I photograph and label by hand | Accuracy per nutrient within rounding tolerance; text error rate on ingredients | Broken down by language (fr/ar) and photo quality. |
| E2a | **Nutri-Score formula** | ~5,000 Open Food Facts products with a stored grade | Agreement with reference grade | Should be ~100%. Each mismatch is investigated: bug or category edge case. |
| E2b | **End-to-end grade** | The same 40 photographed products | Grade agreement; how often an extraction error flips the grade | Measures **error propagation** from extraction into the score. |
| E3 | **Tagging** | 500 held-out OFF products + my 40 | Precision/recall/F1 per allergen and per additive | **Allergen recall is the headline metric**: a missed allergen is worse than a false alarm. |
| E4a | **Explanation citations** | Every additive in E3's test set | % of explanations citing the correct entry ID | Fully automatic. |
| E4b | **Explanation groundedness** | Sample of 100 explanations | % with no claim missing from the retrieved entry | Judged by an LLM; the judge is validated first against 30 hand-labelled cases, and its agreement rate is reported. |

### 8.3 The fine-tuning experiment

Same E3 test set, three configurations:

| Configuration | Allergen recall | Additive F1 | Cost / 1k products | p95 latency |
|---|---|---|---|---|
| Large model, prompt only (baseline) | | | | |
| Small model, prompt only | | | | |
| Fine-tuned small model | | | | |

The result is reported whatever it is. If fine-tuning doesn't beat the baseline, the README explains why.

### 8.4 Leakage and label quality

- OFF data is split by barcode, so no product appears in both training and test sets.
- My 40 photographed products are never used for training.
- **Known weakness:** some OFF allergen and additive tags come from OFF's own ingredient parser, not from humans. A model trained on them may learn to imitate the parser. That's why the hand-checked set is reported separately, and why it's the number I trust most.

### 8.5 In CI

| When | What runs | Cost |
|---|---|---|
| Every push | Unit tests, Nutri-Score formula tests (E2a), harness tests | Free |
| PRs touching prompts, models or AI code | Eval subset: 10 photos, 100 tagging cases | ~cents |
| Nightly / manual | Full eval, report committed to `eval-food/reports/` | ~$1–2 |

The `Gate` fails a PR if any headline metric drops more than 3 points below baseline.

## 9. Non-functional targets

| Target | Value |
|---|---|
| End-to-end processing | p95 < 20 s per product |
| Cost | Measured and reported; target < $0.02 per product |
| Upload | Up to 3 images, 10 MB each, JPEG/PNG |
| Malformed LLM output | Validate → 1 retry with the error fed back → `FAILED`, shown to the user |
| Secrets | None in the repo. OIDC from GitHub Actions to Azure. |
| Disclaimer | Visible in the UI: label information only, not dietary or medical advice. |
| Data licence | Open Food Facts data is under ODbL; attribution in README and UI. |

## 10. Tech stack

Java 21 · Spring Boot 3 · Spring AI (Azure OpenAI) · PostgreSQL + pgvector · Flyway · Thymeleaf + htmx · JUnit 5 + Testcontainers · Docker · GitHub Actions · Azure Container Apps, Azure Database for PostgreSQL Flexible Server, Blob Storage, Application Insights, Azure OpenAI fine-tuning (Developer tier deployments) · Python (training data prep only).

## 11. Plan (2 weeks)

| Day | Work |
|---|---|
| 1 | Spec, data plan, photograph the first 20 products. |
| 2 | Spring Boot skeleton, Flyway, Testcontainers. Check Azure model quota. Photo upload + extraction. |
| 3 | Nutri-Score calculator + E2a against OFF reference data. |
| 4 | `eval-core` harness: Dataset, Scorer, Report, Gate. Wire E1 and E2b. |
| 5 | Photograph and label the remaining 20 products. Prompt-only tagging + E3 baseline. |
| 6–7 | `training/`: OFF data prep, split, fine-tune job. Run the three-way comparison. |
| 8 | Additive RAG + E4a/E4b, including judge validation. |
| 9 | Cost/latency tracking, sanity checks, error handling. |
| 10–11 | UI, Docker, GitHub Actions (CI + eval gate + deploy), Azure deployment. |
| 12 | README with eval tables, architecture diagram, decisions; demo video. |
| 13–14 | Buffer. |

## 12. Risks

| Risk | Mitigation |
|---|---|
| Vision or fine-tuning not available in my Azure region | Check day 2. Spring AI allows swapping provider by config. |
| Fine-tuned deployment costs | Developer tier only (no hourly hosting fee, 24 h lifetime); redeploy for each eval run. |
| $100 credit runs out | Budget alert at $25, stop Postgres when idle, scale-to-zero app, eval subsets in CI. |
| OFF labels are noisy | Hand-checked set reported separately (8.4). |
| Nutri-Score algorithm version mismatch with OFF data | Verify which version OFF's stored grade uses before running E2a. |
| Scope creep | Non-goals in section 2; new ideas go in `docs/v2-ideas.md`. |

## 13. Definition of done

- [ ] Live demo works and scales to zero.
- [ ] `docker compose up` runs everything locally.
- [ ] CI: build, tests, eval gate, image push, deploy.
- [ ] All eval tables in section 8 filled with real numbers, committed.
- [ ] README: problem, architecture, design decisions, eval results, fine-tuning verdict, cost per 1,000 products, "applying the harness to other domains" section.
- [ ] 2-minute demo video.