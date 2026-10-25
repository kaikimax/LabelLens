# Day 5: First eval of the label reader

**Date:** 2026-10-25 · **Model:** `gpt-4.1-mini-2025-04-14` · **Prompt:** `read-label.md` (sha256 `bfb11e26a742`) · **Test set:** 15 photographed products × 3 runs · **Comparison rule:** decisions log, 2026-10-25 · **Command:** `mvn -pl app test -Peval`

> **Read this first:** 15 products is too few to quote these numbers anywhere. One product moves a score by about 7 points. These are internal numbers, and the point of this run is the list of failures, not the percentages.

---

## Two runs: before and after correcting the answer key

Between the two runs, **only `labels.json` and the app's safety settings changed. The model and the prompt are the same** (same prompt hash).

| Score | Run 1 (13:11) | Run 2 (14:40) | Why it moved |
|---|---|---|---|
| Answered | 42 / 45 (93.3%) | 43 / 45 (95.6%) | p003: timeouts → answer-length cap (see below) |
| Energy | 86.7% | 88.9% | p003 answered once |
| Fat | 86.7% | 88.9% | |
| Saturated fat | 93.3% | 95.6% | |
| Carbohydrates | 80.0% | 88.9% | p015 key fixed (0.0 is printed), p003 answered once |
| Sugars | 80.0% | 80.0% | |
| Fibre | 93.3% | 95.6% | |
| Protein | 86.7% | 86.7% | |
| Salt | 86.7% | 88.9% | |
| Type | 93.3% | 95.6% | |
| **Ingredients** | **66.7%** | **82.2%** | **answer-key typos fixed (p006, p013, p010)** |
| **Grade (Test 2b)** | 19 / 24 (79.2%) | 18 / 24 (75.0%) | every miss is p003 or p007 |
| Stability | 97.3% | 97.3% | |
| Cost | $0.099 | $0.119 | |
| Average time per reading | 16.4 s | 10.1 s | run 1 included p003's 2-minute timeouts |

**The biggest jump (ingredients, +15.5 points) came from fixing the answer key, not the AI.** Before trusting an eval score, the answer key gets evaluated too. Same lesson as day 3 (p004) and day 4 (the data filter).

---

## Answer-key errors found by the eval

The eval flagged these as AI errors. Checked against the packages, the **key** was wrong:

| Product | Typed | On the package |
|---|---|---|
| p006 | `Mais`, `arome`, no final period | `Maïs`, `arôme`, final period |
| p013 | `acidifant`, `argumes` | `acidifiant`, `agrumes` |
| p010 | `Vinegar` | **`Vineger`** (the label's own spelling mistake; the AI copied it correctly 3/3) |
| p015 | carbohydrates `null` | `0.0` is printed |
| p003 | `زيت الصوجا (35)` | `(%35)` |
| p015 | sugars and protein `null` (found after run 2) | `0` is printed: the AI was right 3 / 3 |

The strict accent rule did its job: it caught real differences. They were in the key, not in the AI's reading.

---

## Real AI errors (run 2)

### 1. p007: per-portion table read as per 100 g. Grade E shown as **A**, 3 / 3 runs

US-style table per 5 ml serving. The AI returned the per-serving values without converting: energy 5 kcal → 20.92 kJ (once, **5.0** = kcal reported as kJ), carbohydrates 1, sugars 0, salt 0 / 0.17. Grade **A (0)** instead of **E (26)**, every run.

**The most serious finding:** a very salty, sugary sauce shown with the best possible grade.

**Diagnosed through the app** (upload of the same two photos, raw answer from `label_reading.raw_response`):

```
"basis": "100g", "serving_size": null,
"energy": 5, "energy_unit": "kcal",
"carbohydrates": 1, "sugars": 0, "salt": 0,
"sodium": 170, "sodium_unit": "mg"
```

Two separate errors, both by the model:
1. **Wrong basis.** The label says "Serving size 1 tsp (5 ml)", but the AI answered `basis: "100g"` with no serving size. Java can only convert per-portion values when the AI says they're per portion, so nothing was multiplied by 20. (It's also a liquid: `100ml` would have been the right per-100 basis.)
2. **Invented salt.** US labels print sodium, not salt. The AI copied sodium correctly (170 mg) **and** wrote `salt: 0`, which isn't on the label. Java uses salt when it's present, so the real sodium was ignored: 170 mg sodium = 0.425 g salt per serving = 8.5 g per 100 ml, read as **0**.

**Why no warning fired:** the sanity checks look for values that contradict each other. Per-serving values copied as per 100 g are still consistent with each other (5 kcal ≈ 1 g of carbohydrates), so nothing looked wrong. But `salt: 0` next to `sodium: 170 mg` **is** a contradiction the code could detect.

Caveat: even a perfect reading is approximate here (sugars printed "<1 g" per 5 ml = 0 to 20 g per 100 ml), so the key's 20 g is itself an estimate. The grade direction (E vs A) isn't in doubt.

### 2. p003: misaligned table + dense Arabic + two photos. Fails 3 / 3 runs

Three separate problems on one product:
- **Table shifted by one row.** The printed values sit half a row below their labels; the AI matched by position: sugars → null, protein → 0.41 (the sugars value). Same shift in the Azure playground, so it's reproducible, not random. With sugars null, the product can't be graded.
- **Invented ingredients.** The label prints the ingredients **only in Arabic**. With both photos, the AI returned a **French list that isn't on the package** ("Huile de soja (35%), eau, jaune d'œuf, jaune d'œuf en poudre…"), plausible for a mayonnaise but made up. Likely trigger: the prompt says to prefer French ingredients, and the front photo shows French text ("MAYONNAISE Light"). **The model translated, and invented, instead of copying.**
- **Repetition loop.** In 2 of 3 runs it then repeated "jaune d'œuf en poudre, jaune d'œuf déshydraté" until cut off. Before the answer-length cap, this showed up as a 60-second timeout (×2 with Spring AI's own retry, ×4 with the eval's retries = 11 minutes for one product). With `max-tokens: 1500`, it fails in ~25 s and the report shows the repeated text.
- With only the back photo, no loop (6.5 s), but the type became `OIL` instead of `GENERAL`: without the front photo, the AI never sees the word "MAYONNAISE". **The front photo matters for the type.**

### 3. p002: English ingredients copied instead of French, 3 / 3 runs

Multi-language label. The prompt asks for French; the AI copied the English list every time. A prompt-following failure, consistent across runs.

### 4. p009: fat 3.0 read as 0.0 (×2) or 0.3 (×1)

Photo tagged `blurry` + `small-text`. The only product where a value is unstable between runs for image-quality reasons.

### 5. p010: "May Contain Celery." appended to the ingredients, 2 / 3 runs (1 / 3 in run 1)

On the package, the sentence follows the ingredients with no line break. **A prompt gap more than an AI error:** the prompt never says where the ingredients list ends.

---

## What the numbers say (with the sample-size warning above)

- **Every grade failure comes from 2 products.** Everywhere else, reading mistakes stayed inside the same grade.
- **Stability is high (97.3%).** The 4 unstable pairs are all on the hard products (p007, p009, p010). On easy labels, one run would give the same result as three.
- **Situations:** `flat-packaging`, `glare` and `printed-as-less-than` are at 100% on everything, but with 2–3 products each, that proves nothing yet. `small-text` is the weakest (50–67%). `arabic` covers 14 of 15 products, so it's almost the overall score and can't be compared with anything until there are more non-Arabic labels.
- **Cost:** $0.0026 per reading, 10 s on average.

---

## Changes made because of this run

| Change | Why |
|---|---|
| `spring.ai.openai.chat.max-tokens: 1500` | A looping answer ran until timeout. Now it fails in seconds and the report shows it. Normal answers are ~200–300 tokens. |
| Failure reasons include the underlying cause | "Request failed" alone explained nothing; "… <- InterruptedIOException: timeout" did. |
| Failed runs show the end of the AI's answer | That's how the repetition loop became visible. |
| Timeouts are no longer retried by the eval | The same input timed out every time; retrying cost 11 minutes and hid the result. |
| Report output is plain ASCII | `·` and `×` were garbled in the Windows console. |

---

## Prompt ideas (to test one at a time, measured against this run)

1. **Never translate:** copy the ingredients in the language they're printed in; if several languages are printed, take French if present, otherwise the first one. Expected to fix p003 (invented French list) and maybe p002.
2. **Where the list ends:** stop before "may contain" / "peut contenir" / "traces". Expected to fix p010.
3. **Per-portion tables:** when the table is per serving, report `basis: serving` and the serving size, never per-100 g values. Expected to fix p007 (confirmed cause: `basis: "100g"`, `serving_size: null`).
5. **Salt or sodium, never both:** if the label prints only sodium, salt must be `null`. Expected to fix p007's salt.

And one **code** change (no prompt involved), **done after this run:** sanity rule f in `SanityChecks` warns when both salt and sodium are given and disagree (salt ≠ sodium × 2.5, within 0.1 g or 20%). On p007 it now reports *"salt (0.00 g) doesn't match sodium (170 mg = about 0.43 g salt)"*. It flags the problem; it doesn't change the value or the grade.
4. **Misaligned tables:** match each value to its label by order in the list, not by vertical position. Expected to help p003.

Rule for later: **one change per eval run**, so each score change can be attributed to one cause.

---

## Next steps

1. Save the baseline once the key is settled: `mvn -pl app test -Peval "-Deval.saveBaseline=true"`.
2. More products, prioritising variety: non-Arabic labels, per-portion tables, gluten (allergen test set for day 6).
