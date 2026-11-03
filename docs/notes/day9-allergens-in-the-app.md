# Day 9: allergens in the app, AI costs, error handling

## What the app does now (2026-11-03)

After the reading and the Nutri-Score, `AllergenDetector` runs the eval's "combined" setup on the
reading (decisions log, 2026-10-31 and 2026-11-03):

1. `MayContainSplitter` on the ingredients text, in case the reader left a "may contain" sentence in;
2. **contains** = word list ∪ gpt-4.1, on the ingredients part;
3. **mayContain** = word list, on the printed "may contain" sentence.

`GET /api/products/{id}` has an `allergens` block: `contains`, `mayContain`, `aiChecked`,
`foundByWordList`, `foundByAi`, `aiModel`, `aiProblem`, `ingredientsRead`. Stored in
`allergen_result` (V5). Every call is logged in `ai_call` as `FIND_ALLERGENS`.

- **AI down or unusable answer:** the product is still DONE, with the word list result and
  `aiChecked: false` (the word list alone caught 96.7% on the French test set).
- **No ingredients read:** `ingredientsRead: false`. An empty list never means "no allergens"
  unless something was checked.

Two problems found while wiring it:
- **The reader threw the "may contain" sentence away** (rule 7 of the prompt), so the app had
  nothing to find traces in. It now copies it into a new field, `may_contain_text`.
- **"May contain nuts." found nothing.** The word list had specific nuts (almond, hazelnut…) but
  not English "nut" itself. Added, with exceptions for "nut-free" and "tiger nut". The eval never
  showed it: the 500 test texts are almost all French. Found by `AllergenDetectorTest`.

## The reader prompt had to change, and it cost some grade accuracy

New in `read-label.md`: the `may_contain_text` field; "stop at the last ingredient" now really in
rule 7 (the 2026-11-02 decision said so, the prompt didn't); "never translate".

**First try (prompt 37d168257632): p003 timed out in all 3 runs.** With a 2,000-token cap on the
answer (`SpringAiVisionModel.MAX_ANSWER_TOKENS`), the cut-off answer showed why: the model was
**translating p003's Arabic-only list into French** and looping on "jaune d'œuf en poudre, jaune
d'œuf déshydraté, …" until the 60 s timeout. Same weakness as run 3 (p003 and p016 partly
translated), worse with the new prompt. Fix: "**Never translate**: copy a language only where it
is printed." The cap stays: in the app, a runaway answer now fails in seconds instead of a minute.

**Final prompt (2b50e3a6c422), run twice, saved as the baseline:**

| Check | Run 3 (old prompt) | New prompt, run A | New prompt, run B (baseline) |
|---|---|---|---|
| answered | 100% | 100% | 100% |
| energy | 92.5% | 95.7% | 96.8% |
| fat | 96.8% | 96.8% | 96.8% |
| saturated fat | 100% | 96.8% | 96.8% |
| carbohydrates | 96.8% | 93.5% | 93.5% |
| sugars | 90.3% | 87.1% | 87.1% |
| fibre | 100% | 100% | 100% |
| protein | 90.3% | 89.2% | 89.2% |
| salt | 95.7% | 96.8% | 96.8% |
| type | 96.8% | 95.7% | 95.7% |
| ingredients | 73.1% | 74.2% | 72.0% |
| **grade** | **84.2%** | **77.2%** | **77.2%** |
| stability | 97.3% | 95.7% | 96.4% |

**The grade drop is real, not luck:** the same 77.2% in both runs. It comes from 3 products whose
nutrition table now reads worse, although the rules changed only concern the ingredients:
- p009: saturated fat `null` or 0.8 instead of 2.1 → not scored or wrong grade;
- p003: sugars `null`, protein shifted (Arabic table rows) → not scored;
- p026: carbohydrates and sugars rows shifted (Arabic table).

Kept anyway: without the new prompt the app can't see "may contain" sentences, and the reader
can loop for a minute on Arabic-only labels. With 31 products, one product read wrong in its 3
runs moves a score by 3.2 points, so these 3 products are the whole difference.

Still wrong with both prompts (reader errors, key checked against the photos): p002 and p028
(English list chosen over French), p007 (per-portion table), p017 and p016 (Arabic lists
rewritten), p018 (`null`: a single ingredient without a heading), p021 (text added after the
list), p029 (protein), p030 (processed cheese read as CHEESE).

## AI costs: `GET /api/ai-costs` (2026-11-03)

Built from the `ai_call` table: one row per step and model (calls, failures, tokens, estimated
cost, average and longest time), then a cost and time **per product for each step**, added up.

- **Prices** are list prices per million tokens (`ModelPrices`), matched by the longest model-name
  prefix: Azure answers "gpt-4.1-mini-2025-04-14", which also starts with "gpt-4.1" (5x the price).
  A model with no known price is listed in `unpricedModels`, never counted as free.
- **Per product = sum of the steps**, each step's cost divided by the products that went through
  it. The first version divided the total by all products: day 3 uploads have no allergen call,
  so it said $1.65 per 1,000 instead of $2.01.

First real numbers (9 uploads, only 2 with the allergen step, so a small sample):

| Step | Model | Cost per product | Time per product |
|---|---|---|---|
| Reading | gpt-4.1-mini | $0.0015 | 6.3 s |
| Allergens | gpt-4.1 | $0.0005 | 1.1 s |
| **Total** | | **$0.0020 → about $2 per 1,000 products** | **7.5 s** |

The reading eval measures $0.0025 per reading with today's prompt (more photos per product than
the day 3 uploads), so expect about $2.50–3.00 per 1,000 in practice. The allergen step is about
a fifth of the cost; base nano would save ~$0.47 per 1,000 for 5x the false alarms (decisions log,
2026-10-31).

## Error handling

- **An unexpected error after the reading** (a bug, a database error in the last step) used to
  leave the product in `PENDING` forever, with nothing logged. It now marks it `FAILED` with a
  short reason ("Unexpected error while processing the photos (IllegalStateException)…"); the
  stack trace goes to the server log, the internal message never to the API.
- **Logging** (there was none): failed readings and allergen checks done without the AI are
  logged as warnings, unexpected errors as errors with the stack trace.
- **A reader that loops** fails in seconds (2,000-token cap) instead of after the 60 s timeout.
- Tested: `ProductApiTest` makes the allergen step throw and checks the product ends `FAILED`
  with nothing half-saved.

Known limit: if the app process itself dies in the middle of an upload, that product stays
`PENDING`. Rare for a single-instance app; not handled.

## Not measured yet

The allergen scores (day 6, day 7) are on Open Food Facts **texts**. In the app, reading errors
come first: a word misread on the photo can hide an allergen. `labels.json` has `allergens` and
`may_contain` for all 31 products, so a photo → allergens eval is possible; until then the README
must say the allergen numbers assume a correct reading.

## Next

- Prompt work on the nutrition table: Arabic table rows (p003, p016, p026), p009 saturated fat.
  The baseline now measures it.
- Photo → allergens eval on the 31 products (cheap: one gpt-4.1 call per reading).