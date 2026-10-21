# LabelLens — Build Plan

Day-by-day plan and decisions taken along the way. The overall timeline is in SPEC.md section 13; this file has the detail for the current day.

---

## Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-10-12 | Nutri-Score 2023 formula | Open Food Facts grades use it (`misc_tags`). |
| 2026-10-12 | Only the 14 EU allergens | Open Food Facts also tags pork, gelatin, beef: dietary flags, not allergens. |
| 2026-10-17 | Spring Boot 4.1.1, Java 21 | Spring AI 2.0 requires Boot 4. |
| 2026-10-17 | Local Docker database on port 5433 | A locally installed PostgreSQL already uses 5432. |
| 2026-10-17 | Azure OpenAI resource `labellens`, Spain Central, Standard S0 | Student subscription region policy. Fine-tuning is available from this resource. |
| 2026-10-17 | Fixed model versions only; no `model-router`, no `gpt-chat-latest` | Those change model silently, so eval scores could move without any change on my side. |
| 2026-10-17 | Models: see table below | |

### Models

| Role | Model | Deployment type | When deployed |
|---|---|---|---|
| Reading label photos | `gpt-4.1-mini` | Global Standard | Day 3 |
| Allergens, big model (baseline to beat) | `gpt-4.1` | Global Standard | Day 6 |
| Allergens, small model, instructions only | `gpt-4.1-nano` | Global Standard | Day 6 |
| Allergens, small model, fine-tuned | `gpt-4.1-nano` + fine-tuning | Developer tier (no hourly fee, 24 h lifetime) | Days 7–8 |
| Possible later comparison for reading | a newer model with image input (e.g. from the gpt-6 family) | Global Standard | After day 5, once the eval exists |

---

## Day 3 — The AI reads the label

### Goal
Upload a photo → the app sends it to `gpt-4.1-mini` → gets back the nutrition values and ingredients as JSON → checks them → saves them. The product ends as `EXTRACTED` (with warnings if something looks wrong) or `FAILED` (with the reason).

At the end of the day, this works on a real photo:
```
curl.exe -F "photos=@eval-food/data/photos/images/p001_back.jpg" http://localhost:8080/api/products
→ status EXTRACTED, energy 243.7 kJ, protein 3 g, ingredients "lait entier sans lactose…"
```

### Not today
Nutri-Score (day 4), the eval harness (day 5), allergens (day 6). Resist adding them.

### Before starting
- [ ] `gpt-4.1-mini` deployed in Foundry: Global Standard, name `gpt-4.1-mini`, rate limit 10–20K tokens per minute.
- [ ] Endpoint URL and API key copied from **Models + endpoints**.
- [ ] Day 2 committed and pushed.

### Step 1 — Secrets as environment variables (15 min)
The key never goes in a file in the repo. The app reads three environment variables:

| Variable | Value |
|---|---|
| `AZURE_OPENAI_ENDPOINT` | the endpoint URL from Foundry |
| `AZURE_OPENAI_API_KEY` | the key |
| `LABELLENS_READER_DEPLOYMENT` | `gpt-4.1-mini` |

Set them in your IDE's run configuration, or in a local `.env` file (already in `.gitignore`). Never with `setx`: that would expose the key to every program on your PC.

### Step 2 — Add Spring AI (30 min)
- Add the Spring AI 2.0 BOM to the root `pom.xml`, and the OpenAI starter to `app/pom.xml`.
- **Check the Spring AI 2.0 docs for how to point it at Azure.** In 2.0, the separate Azure OpenAI integration was merged into the OpenAI one, so older tutorials (with `spring-ai-azure-openai`) are out of date.
- Tests must **never** call the real Azure API: it costs money, it's slow, and the answers vary. Tests use a fake model (step 6).

### Step 3 — The shape of the answer (30 min)
A Java record `LabelReading` with **the same fields and names as `labels.json`**, so the day 5 eval can compare the AI's answer with your hand-typed answer field by field, without any translation:

```
name, brand, type (GENERAL / DRINK / CHEESE / OIL)
per_100g: energy_kj, fat, saturated_fat, carbohydrates, sugars, fibre, protein, salt
ingredients_text, ingredients_language
```

Two layers: what the model returns is the **printed** table (values + `basis` + `serving_size` + energy unit + salt or sodium). A Java converter then produces the per-100 g `LabelReading` above, which is what gets stored and compared with `labels.json`. US-style labels (per serving, kcal, sodium in mg) are common on imported products, so this isn't an edge case.

`fat` and `carbohydrates` are new. Nutri-Score doesn't use them, but the sanity checks in step 5 need them. (DATA_PLAN.md and `labels.json` are updated to include them.)

Every number can be `null`: "not printed" must stay different from 0.

### Step 4 — The instructions to the model (1 h)
A prompt file in `app/src/main/resources/prompts/read-label.md` (not a Java string, so prompt changes show up clearly in Git history). It tells the model:
- Copy the values **exactly as printed, in the units printed**, and say what they refer to: `basis` = `100g`, `100ml` or `serving`, plus `serving_size` (e.g. 5 ml) when the basis is a serving. If both per 100 g and per serving are printed, take per 100 g.
- Energy as printed, with its unit (`kJ` or `kcal`); sodium as printed if salt isn't printed.
- **The model never converts anything.** Java converts to per 100 g, kcal to kJ (× 4.184) and sodium to salt (× 2.5). Code can't get arithmetic wrong; a model can.
- `null` when a value isn't printed or can't be read. **Never guess.** A wrong number is worse than a missing one.
- Copy the ingredients text **exactly**, in the main language on the label.
- Answer with JSON matching the `LabelReading` format, nothing else.

Send the photos as images in the same request.

### Step 5 — Checking the answer (1–2 h)
In this order, in plain Java:

1. **Format check:** does the JSON load into `LabelReading`? If not, ask the model **once** more, including the error message ("field `salt` must be a number"). If it fails again, mark the product `FAILED` with the reason.
2. **Sanity checks**, each producing a warning, not a failure:
   - nothing above 100 g per 100 g;
   - `sugars` ≤ `carbohydrates`;
   - `saturated_fat` ≤ `fat`;
   - **energy matches the macros**: protein × 17 + carbohydrates × 17 + fat × 37 + fibre × 8 should be within 20% of `energy_kj` (15% was too strict: real labels like p006 are 17% apart). This catches many misread numbers, since one wrong digit breaks the sum.
3. Save warnings with the reading, so the UI and the eval can show them.

Write these checks as small pure functions: no Spring, no database. They're the easiest code in the project to test fully.

### Step 6 — Database and wiring (1–2 h)
- `V2__create_label_reading.sql`: a `label_reading` table (one row per product) with the step 3 fields, `warnings` as JSONB, the model name, and the raw model answer as text (invaluable when debugging a bad reading).
- `V3__create_ai_call.sql`: an `ai_call` table recording **every** call to the model: purpose (`READ_LABEL`), model, input tokens, output tokens, duration in ms, success or failure. The spec plans cost tracking for day 9, but recording it from the first call costs 20 lines now and saves rewriting later.
- A `failure_reason` column on `product`.
- `ProductService.create`: save photos → call the reader → save the reading → status `EXTRACTED` or `FAILED`. Still synchronous (the request waits); the spec says so.

### Step 7 — Tests (1–2 h)
- **Sanity checks:** unit tests with hand-made `LabelReading`s. Include the milk from p001, where sugars are printed as 0 but carbohydrates as 4.7: the check `sugars ≤ carbohydrates` passes, which shows the check can't catch every label error. Write that down in a test name; it's a real finding.
- **Reader with a fake model:** the fake returns (a) valid JSON → `EXTRACTED`; (b) broken JSON twice → `FAILED` with a reason; (c) broken then valid JSON → `EXTRACTED` after one retry.
- **API test:** upload with the fake model → status `EXTRACTED` and the values appear in `GET /api/products/{id}`.

### Step 8 — First real run (30 min)
With the real Azure deployment, upload 3–5 of your photos. For each one, compare by eye with your `labels.json` entry and note what it got wrong. **Don't fix the prompt yet**: day 5 builds the tool that measures this properly. Just write the notes in `docs/notes/day3-first-run.md`, and check the `ai_call` table for tokens and duration.

### Done when
- [ ] `mvn verify` passes, with no call to the real Azure API.
- [ ] A real photo goes through, and the reading is visible in `GET /api/products/{id}`.
- [ ] A broken model answer ends as `FAILED` with a readable reason, not a crash.
- [ ] Every real call has a row in `ai_call`.
- [ ] The API key appears in no committed file (`git grep -i "api-key"` finds nothing secret).
- [ ] Committed and pushed.

### Realistic size
About 6–9 hours of work. It may take two days at your pace, and that's fine: this is the core of the app. If it runs long, cut the retry (step 5.1) before cutting the sanity checks or the tests.