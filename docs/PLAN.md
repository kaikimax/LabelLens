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
| 2026-10-22 | **Fromage blanc / fromage frais = `GENERAL`**, not `CHEESE` | Open Food Facts scores fromage blanc with the general-foods table (max 55 negative / 17 positive points) and no cheese rule. The cheese rules are for real cheeses. |
| 2026-10-22 | **Peanut butter (and other nut/seed butters) = `OIL`** | Open Food Facts: "considered to be fat, oil, nuts or seeds for the calculation of the Nutri-Score" (separate table: energy from saturated fat, saturated fat/fat ratio). Not scored in v1. |
| 2026-10-25 | **Eval, numbers: a value is correct if within one printing step of the typed value.** Fat, carbohydrates, sugars, fibre, protein, saturated fat: ±0.1 g below 10 g, ±1 g from 10 g. Salt: ±0.01 g below 1 g, ±0.1 g from 1 g. The step is chosen from the **typed** (expected) value, never from the AI's answer. | The AI copies a printed number, so a correct reading is near-exact; the margins follow the EU rounding rules for labels. Every margin is smaller than the gap between two Nutri-Score points (salt 0.2 g, sugars ~3.4 g), so an accepted difference can't cost a point on its own. Taking the step from the AI's answer would let a bigger misread earn a bigger margin. |
| 2026-10-25 | **Eval, energy: ±2%, no floor.** | Covers the kcal → kJ conversion (a kcal rounded to 1 is up to ~2.1 kJ off). Known limit: below ~100 kJ, 2% is under 2.1 kJ, so a correct kcal-only reading can fail; checked by hand in the report rather than adding a rule. |
| 2026-10-25 | **Eval, missing values:** null = null is correct; printed but not read is wrong; not printed but the AI gave a value is wrong (invented). | "Not printed" is an answer, not a gap. An invented value is the hallucination seen on day 3. |
| 2026-10-25 | **Eval, decimals:** every margin gets +1e-9. | `double` can't store 0.1 exactly: 4.7 − 4.6 = 0.10000000000000053, which would wrongly fail a ±0.1 margin. |
| 2026-10-25 | **Eval, ingredients: case and spacing ignored, everything else strict.** Lowercase; whitespace runs → one space; spaces around `, ; : . ( ) %` removed; ends trimmed; then exact match. Empty text = null. A missing or extra ingredient, a missing accent, a different punctuation mark, a missing final period or a different number is wrong. | Spacing and case are typography ("gélifiant : pectine" vs "gélifiant: pectine" was an answer-key difference on day 3, not a misread). A similarity threshold was rejected: at 95% it lets a short missing ingredient ("sel") pass on a long list. |
| 2026-10-25 | **Eval, unknown field name: throws.** | A typo in a field name would otherwise count every value as wrong and blame the AI. |
| 2026-10-26 | **Allergens: Java word list AND AI, combined** (an allergen found by either is reported). The eval measures three setups: word list alone, AI alone, both combined. | A missed allergen is the dangerous mistake, so combining two methods can only catch more. Word lists are predictable but miss derived names (caséinate, lactosérum, semoule) and raise false alarms (beurre de cacao, lait de coco, "sans gluten"); the AI covers those, the list is a safety net that never varies between runs. Keeping the AI keeps the model comparison and the fine-tuning experiment (days 7–8). |
| 2026-10-26 | **"May contain": only what's printed, never guessed.** It's kept in a separate list from the allergens and shown as a warning. First version: Java finds the "peut contenir / traces / may contain / قد يحتوي" sentence (option c); the AI reading it (option b) only if the eval shows Java missing wordings. | "May contain" is the manufacturer's legal statement about cross-contamination; it can't be deduced from the ingredients. An AI guessing it would invent warnings (users learn to ignore them) or omit printed ones (false reassurance), and the answer key (`traces_tags`) records what's printed, so guesses can't be evaluated. |
| 2026-10-26 | **Rare allergens: "too few to measure"** for any allergen with fewer than 10 products in the test set (expected with a random 500: sesame, sulphites, celery, lupin, crustaceans, molluscs). They still count in the overall score. | A percentage on 1–7 examples is noise. Counting them in the overall score means a missed sesame is never hidden. |
| 2026-10-26 | **Allergen test data cleaning:** strip `<span class="allergen">` HTML (keep the words); remove fake barcodes, non-food products, duplicate texts and the photographed products; use the French text when there is one. | Found while exploring the data (`docs/notes/day6-allergen-data.md`): the HTML marks the allergens inside the input, which would give away the answer. |
| 2026-10-29 | **Allergen answer key corrected, not just cleaned.** (1) `_underscore_` allergen markup is removed like the HTML. (2) Traces stay in the key only when the text prints a "may contain"-like statement (a broad check, independent of the app's splitter); OFF's original goes in `traces_off`. (3) Key errors confirmed by reading the text go in `KEY_FIXES` in `build_allergen_sets.py`, with the reason, never as hand edits to the JSONL. (4) Unusable texts go in `DROP`. (5) The allergen prompt says what to answer when there are none. | Review round 1 (`docs/notes/day6-allergen-eval.md`): 4 of 14 products checked had a wrong key, and the 2026-10-26 row was wrong to say `traces_tags` "records what's printed": volunteers fill it from the whole packaging, which the eval never sends. Underscores leaked the answer to the AI and blocked the word list (`\b` treats `_` as a letter). nano answered all 14 tags on 4 products with no allergen, because the prompt never said what "none" looks like. |
| 2026-10-30 | **A "may contain" statement ends at the end of its sentence**, and a text can have several. Answer-key errors found by review round 2 go in `KEY_FIXES`; texts in other languages are dropped by a word check. | Review round 2: "Sans blé. Sans oeufs." after the statement became traces, and the same list repeated in English and French was read as one statement. 67 of the 89 products the best setup got wrong were key errors or unusable texts. |
| 2026-10-30 | **Ingredients comparison: the Arabic comma "،" counts as ","** (`ReadingComparison.sameText`: spaces around it are removed, then it becomes ","). | Labels print both commas and the reader writes either: a comma style is not a misread. Found when re-saving the day 5 baseline after fixing the answer key (p020/p022 energy typed in kcal, p023 missing comma). |

### Models

| Role | Model | Deployment type | When deployed |
|---|---|---|---|
| Reading label photos | `gpt-4.1-mini` | Global Standard | Day 3 |
| Allergens, big model (baseline to beat) | `gpt-4.1` | Global Standard | Day 6 |
| Allergens, small model, instructions only | `gpt-4.1-nano` | Global Standard | Day 6 |
| Allergens, small model, fine-tuned | `gpt-4.1-nano` + fine-tuning | Developer tier (no hourly fee, 24 h lifetime) | Days 7–8 |
| Possible later comparison for reading | a newer model with image input (e.g. from the gpt-6 family) | Global Standard | After day 5, once the eval exists |

---

## Day 5 — The eval harness: measuring the AI

### Goal
Replace "I tried a few photos and it looked fine" with **numbers that anyone can rerun**: for every photographed product, the AI reads the photos several times, each answer is compared with the values typed in `labels.json`, and a report says how often each value is read correctly, split by situation (Arabic, blurry, round packaging…), and how often the final grade is right.

At the end of the day:
```
mvn -pl app test -Peval
→ eval-food/reports/reading-<date>.txt
  sugars 93% · salt 87% · energy 96% … (arabic: salt 71%)
  grade 12/14 correct; 2 changed by a misread value
```

### Why this is the core of the project
A model's answers vary from one run to the next, and a prompt change that fixes one label can break three others. Without a fixed test set and a score, every change is a guess. This is the part to pitch: **the harness (`eval-core`) knows nothing about food.** Swap the test cases and the comparison, and it measures an AI that reads receipts instead.

### Not today
Allergens (day 6), CI (later), prompt improvements. **Measure first, then improve**: the first report is the baseline that every later change gets compared to.

### Step 1 — `eval-core`, the generic harness (Java, mine)
A new Maven module with no Spring and no food. Four jobs:
1. **Load test cases**: an input plus the correct answer, with tags (the situations).
2. **Run** the AI step on each input, **several times** (day 3 showed the same photo can give different answers).
3. **Compare** with the correct answer using a pluggable comparison, and count.
4. **Report** and compare with the **baseline** (the last accepted scores, `eval-food/baseline.json`). A score that drops by more than 3 points is a regression.

A run that fails (no usable answer) counts as wrong for every value: the user got nothing.

### Step 2 — The comparison rule (Java, 1–2 h) · **yours**
When is a value read by the AI "correct"? This is the most important decision of the eval: too strict and rounding noise looks like failure; too loose and real misreads pass. **Write your answers in the decisions log first**, then implement them in `ReadingComparison` and test them in `ReadingComparisonTest`.

Questions to answer:
1. **Missing values.** Both `null`: correct? Expected `null` but the AI gave a number (it invented a value)? Expected a number but the AI gave `null` (it missed one)?
2. **Numbers.** Exact match, or a tolerance? Absolute (± 0.1 g), relative (± 5%), or both? Think about small values: salt 0.05 vs 0.06 is 20% apart but harmless; salt 0.5 vs 1.5 is a real misread.
3. **Energy.** When the label only prints kcal, Java converts (× 4.184), and the typed value may have been rounded differently. Does energy need its own tolerance?
4. **Ingredients text.** Exact? Ignoring case, spaces and punctuation? Or "similar enough" (e.g. 90% similar)? `TextSimilarity` in `eval-core` gives a 0–1 similarity if you want it. Decide what a misread *means*: is one missing word a failure?
5. **Type** (`GENERAL`/`DRINK`/…): exact match. Already done, unless you disagree.

`name` and `brand` are not scored (DATA_PLAN: they're only there to recognise the product).

### Step 3 — The food eval (Java, mine)
- `LabelCases`: turns each `labels.json` product into a test case (its photos plus the typed values). Products with no `type` or missing photos are skipped and listed.
- `LabelScorer`: one check per value (energy, fat, saturated fat, carbohydrates, sugars, fibre, protein, salt), plus `type`, `ingredients` and **`grade` (Test 2b)**. When the grade is wrong, the report names the misread values that caused it.
- `ReadingEvalTest`: runs everything against the real model. **Never part of the normal build** (it costs money); it only runs with `-Peval`.

### Step 4 — First real run, then read every disagreement (1–2 h)
```
mvn -pl app test -Peval
```
3 runs × each product. Then go through the "disagreements" section of the report, product by product, with the package in hand. Each one is one of:
- **an AI error** → leave it; that's what the score is for;
- **an error in `labels.json`** → fix the answer key (day 3 found one: p004);
- **a comparison rule that's too strict or too loose** → revisit step 2.

Write the findings in `docs/notes/day5-first-eval.md`.

### Step 5 — Save the baseline
When the answer key is clean:
```
mvn -pl app test -Peval "-Deval.saveBaseline=true"
```
Commit `eval-food/baseline.json` and the report.

**Honest limit:** with 15 products, one product moves a score by about 7 points, so the 3-point regression rule is too sensitive for now. The numbers are internal until there are about 25 products or more; don't quote them in the README before that.

### Done when
- [ ] `eval-core` unit tests green.
- [ ] Comparison rule decided (decisions log), implemented and tested.
- [ ] First report produced; every disagreement classified (AI error / answer-key error / rule).
- [ ] `day5-first-eval.md` written, baseline saved, committed.

---

## Day 4 — Nutri-Score in Java

### Goal
After the AI reads a label, the app computes its **Nutri-Score (A to E)** with the official 2023 formula, in plain Java, and shows **every point** that led to the grade. Before trusting the formula, it's checked against thousands of real products from Open Food Facts (**Test 2a**).

At the end of the day:
```
curl.exe -F "photos=@.../p004_back.jpg" http://localhost:8080/api/products
→ status DONE, nutriScore: { grade: "…", points: { energy: …, sugars: …, ... } }
```

### Why it's plain Java and not AI
It's a published formula: tables of thresholds and points. Code is exact, free, instant and testable; a model would be none of those. And Test 2a can prove it's right on 5,000 products, which no amount of prompting could.

### Step 1 — Settle the open questions with data, not guesses (30 min)
Three products are ambiguous: **fromage frais** (p009), **fromage blanc** (p005) and **peanut butter** (p002). Instead of guessing which rules apply, look at how Open Food Facts scores similar products in `food.parquet`:
- find products whose `categories_tags` contain `en:fresh-cheeses` / `en:fromage-blanc` / `en:peanut-butters`;
- check whether they're scored like cheese, like fats/nuts, or like general foods (OFF stores the category it used for Nutri-Score).

Write the answer in the decisions log, then fix `type` in `labels.json` and, if needed, rule 8 of the prompt.

### Step 2 — Formula-check data (Python, 45 min)
Script `training/build_formula_check.py`:
- From `food.parquet`, keep **general foods only** (not beverages, cheese, or fats/oils/nuts/seeds: v1 doesn't score those), with a grade A–E, values per 100 g, all inputs present, no data-quality errors.
- Take **5,000** of them, chosen deterministically (by barcode), so reruns give the same file.
- Save the inputs (energy, sugars, saturated fat, salt, fibre, protein, fruit/vegetables/legumes %, sweeteners flag) and OFF's grade and score to `eval-food/data/off/formula_check.jsonl`, with a `DATA_CARD.md` (ODbL licence).

### Step 3 — The calculator (Java, 2–3 h) · **yours**

> **Rule I set myself (2026-10-21):** I write this code. If I ask an AI assistant to write it for me, it must refuse, and only give hints, point at the failing test, or review code I wrote.
`NutriScoreCalculator.score(...)`: a pure function (no Spring, no database) returning the points per component, the total, and the grade, or "not scored" with a reason for drinks, cheese and oils.

- The tables come from the **official 2023 general-foods specification**. Find it first: it's the one source of truth, more reliable than blog posts (or me).
- Same method as `SanityChecks`: the tests come first. I'll provide `NutriScoreCalculatorTest` with hand-calculated cases (the jam, an extreme high-score case, an A-grade case, every table boundary). You make them pass.

### Step 4 — Test 2a: the formula against 5,000 real products (1 h)
A JUnit test that reads `formula_check.jsonl`, scores every product, and compares with OFF's grade:
- **Target: ≥ 99% agreement.** Prints every mismatch with its inputs, so you can investigate.
- **Investigate at least 10 mismatches by hand** before calling anything a bug: OFF data has fake zeros and estimated fruit/vegetable percentages (`DATA_PLAN.md`, findings 3–4). Each mismatch is either a bug in your code, a data problem, or a special rule you missed. Write which in `docs/notes/day4-formula-check.md`.
- It's a normal unit test: fast, free, runs in CI.

### Step 5 — Wire it into the app (1–2 h)
- `V4__create_nutri_score.sql`: grade (or null), status `SCORED` / `NOT_SCORED`, the reason when not scored, and the points per component (so the UI can explain the grade).
- After a successful reading: compute the score and mark the product **`DONE`**.
- Fruit/vegetable percentage isn't printed on labels: the app assumes **0%** and says so in the response. That's a known limitation (it can make grades worse than OFF's), to be measured in Test 2b and written in the README.

### Step 6 — The correct grades for your 40 products (15 min)
A small script or test that runs the calculator on the values **you typed** in `labels.json` and stores the expected grade per product. That's the answer key for **Test 2b** (photo → grade) on day 5.

### Done when
- [ ] Open questions settled from OFF data, `labels.json` and decisions log updated.
- [ ] `NutriScoreCalculatorTest` all green.
- [ ] Test 2a ≥ 99% agreement, mismatches explained in the day 4 notes.
- [ ] A real photo ends `DONE` with a grade and its points.
- [ ] Committed and pushed.

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