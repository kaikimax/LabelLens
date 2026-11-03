# Day 9: allergens in the app

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

## Not measured yet

The allergen scores (day 6, day 7) are on Open Food Facts **texts**. In the app, reading errors
come first: a word misread on the photo can hide an allergen. `labels.json` has `allergens` and
`may_contain` for all 31 products, so a photo → allergens eval is possible; until then the README
must say the allergen numbers assume a correct reading.

## Next

- Prompt work on the nutrition table: Arabic table rows (p003, p016, p026), p009 saturated fat.
  The baseline now measures it.
- Photo → allergens eval on the 31 products (cheap: one gpt-4.1 call per reading).