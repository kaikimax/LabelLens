# Day 6, part 2: the allergen eval (500 products, 5 setups)

Run on 2026-10-28, 23:04 to 00:20 (76 min, about $0.85 including the 50-product trial).
Test set: `allergen_test.jsonl`, 500 products with French text. 1 run per product.
Reports: `eval-food/reports/allergens-fr-*-20261028-*.txt` / `-20261029-*.txt`.

## Results

| Setup | Caught (recall) | Correct alarms (precision) | Whole set right |
|---|---|---|---|
| word list (Java, free) | 84.6% (539/637) | **93.3%** (539/578) | 82.0% |
| gpt-4.1-nano alone | 89.3% | 82.1% | 77.6% |
| gpt-4.1 alone | 92.5% | 90.8% | 84.6% |
| word list + nano | 93.9% | 79.3% | 79.4% |
| word list + gpt-4.1 | **94.5%** (602/637) | 90.0% | **85.6%** |

"May contain" (traces) is the same in every setup, because it always comes from the word list:
caught 41.9% (147/351), correct 88.0%.

These numbers are measured against an answer key with known errors (see below). They show the
ranking, not the real accuracy.

## What the numbers say

1. **The 50-product trial ranked the setups correctly**, and the 500 run moved every score by
   only a few points. Combined with gpt-4.1 is the best setup on recall and on whole-set right.
2. **Combining helps recall and costs precision.** The union can only add alarms. With gpt-4.1,
   recall rises by 2 points (13 more allergens caught) and precision falls by 0.8. With nano,
   precision falls by 14 points.
3. **gpt-4.1-nano is worse than the free word list** on whole-set right (77.6% vs 82.0%), on its
   own and combined. It over-alarms:
   - **4 products got all 14 allergens** (3270190127765, 6111266963450, 8718754500296,
     3660992012527). The key has none for any of them. That's 56 of combined-nano's 156 false
     alarms, and every false lupin, mollusc and crustacean alarm. Probable cause: the text left
     after removing "may contain" is very short or a list of allergens. To be checked.
   - Without those 4, its precision would be about 85.7%, still below the word list.
   - It adds gluten (34) and milk (32) where the key has none. Some of these could be key errors
     (point 5).
   - This is the case for fine-tuning (days 7–8): a small model that is worse than a word list
     before training.
4. **Word list gaps**, as misses: soybeans 23, nuts 20, milk 14, gluten 11, fish 7, eggs 6. Nuts is
   the weakest (72.6% caught), and the same gap shows in "may contain": in 35 trace mistakes,
   **nuts is the only allergen missing**. That's one word-list gap repeated, probably the wording
   ("fruits à coque" variants, named nuts). Reading the texts will tell.
5. **Likely answer-key errors (Open Food Facts):**
   - **33 false alarms where the word list AND the AI independently found the same allergen** the
     key doesn't have (often milk). Two different methods agreeing is strong evidence that the key
     is missing it, as expected on day 6.
   - **10 products where neither found anything but the key has allergens:** 0769503575353,
     6111255062010, 6111162001225, 3222474768461, 3263670126419 (all fish), 3250390271604,
     3222475104336, 3250390115236, 3250390748847, 5999884034469. If the text doesn't name the
     allergen, the key was filled from somewhere else (category, photo) and the case can't be
     answered from the text.
6. **Traces: 87 of the 139 wrong cases got nothing at all.** The key's traces come from OFF's
   traces field, which contributors fill from the packaging, not only from the ingredients text.
   So many expected traces are probably **not printed in the text we send**. The 41.9% then
   measures the key, not the splitter. Fix in the data, not the code: tag each case with whether
   its text contains a "may contain" phrase, and score traces only on those.

## Harness bug found and fixed

The reports said "Stability: 49.9%" for a 1-run eval, which is impossible. `EvalResult.stability()`
compared counter checks (`caught:all` appears once per allergen in a run), so it compared different
allergens, not different runs. It now skips counters, and the report prints "not measured (needs 2
or more runs per case)" when nothing can be compared. New test:
`EvalRunnerTest.stability_ignoresCounters_soOneRunMeasuresNothing`.

## Hand review, round 1 (14 products, 2026-10-29)

| Group | Products | Verdict |
|---|---|---|
| nano answered "all 14" | 3270190127765, 6111266963450, 8718754500296, 3660992012527 | **AI error, all 4.** The key ([]) is right. All 4 have no allergen: when there's nothing to find, nano answers with the tag list from the prompt. The prompt never says what to answer when there are none. |
| fish, nobody found it | 0769503575353, 6111255062010, 6111162001225, 3222474768461, 3263670126419 | **Key right.** Mackerel and sardines: the word list has no fish species, and nano doesn't treat a species as "en:fish". This corrects point 5 above: I had guessed key errors. |
| gluten/milk, nobody found it | 3250390271604, 3250390115236, 3250390748847 | **Key error.** Cocoa powder, cocoa drink and mint sweets: no gluten or milk in the text. |
| | 3222475104336 | **Key error** on allergens (gluten is only a trace: "traces de _gluten_"). |
| unreadable | 5999884034469 | **Text is OCR garbage**, so the case can't be answered. The junk filter missed it, and 8718754500296 is half garbage too. |

Two more findings from reading the texts:
- **Open Food Facts marks allergens with underscores**: `_gluten_`, `_Sardines_`. `\b` sees `_` as a
  letter, so `\bgluten` never matches `_gluten_`. That explains at least 3222475104336's missed trace,
  and probably many more misses. Real labels have bold type, not underscores, so this is data markup
  like HTML: the cleaning in `build_allergen_sets.py` should remove it.
- **The "may contain" nuts gap is confirmed**: 3270190127765 prints "Peut contenir des traces de
  fruits à coque, de céréales contenant du gluten et de lait". The word list found milk and gluten
  but not nuts. The key misses the gluten trace (another key error).

## Fixes after round 1 (2026-10-29, decisions log)

- `build_allergen_sets.py`: `_underscore_` markup removed; traces kept in the key only when the text
  prints a "may contain"-like statement (original in `traces_off`); `KEY_FIXES` (6 products) and
  `DROP` (1). The tag "html" in the reports is now "markup".
- Prompt: "If there are none, answer {"allergens": []}." (same line in DATA_PLAN, for fine-tuning).
- These change the test set, so the 2026-10-28 scores can't be compared with the next run.

## Next

1. Word list (user's code, hints only): fish species (and the `x` plural: maquereaux); why
   "fruits à coque" isn't found in the 3270190127765 trace sentence.
2. Rebuild the sets, then review round 2: 10 of the 33 shared false alarms (KEY_FIXES grows).
3. Re-run all 5 setups, then save the baselines.
