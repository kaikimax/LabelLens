# Day 4 — Test 2a: Nutri-Score calculator vs Open Food Facts

**Dates:** 2026-10-22 (first run), 2026-10-23 (after fixing the data filter) · **Calculator:** `NutriScoreCalculator` (2023, general foods; tables from the [Scientific Committee's 2022 update report](https://mpc.gouvernement.lu/dam-assets/le-minist%C3%A8re/consodur/2022-main-algorithm-report-update-final.pdf)) · **Data:** `eval-food/data/off/formula_check.jsonl`, 5,000 general foods (see its `DATA_CARD.md`) · **Test:** `FormulaCheckTest`

---

## Results

| Measure | Run 1 (10-22) | Run 2 (10-23, meat tags) | **Run 3 (10-23, milk tag)** |
|---|---|---|---|
| Same grade (strict) | 98.10% | 98.22% | **4,956 / 5,000 (99.12%)** |
| Same score (strict) | 96.30% | 97.02% | 4,904 / 5,000 (98.08%) |
| Mismatches explained by the fruit/vegetable input | 50 | 54 | 35 |
| Unexplained mismatches | 45 | 35 | **9** |
| **Agreement once fruit/vegetable data is set aside** | 99.10% | 99.30% | **99.82%** (threshold: 99%) ✓ |

Between runs, **only the data filter changed, never the calculator.** Each run rebuilds the 5,000-product sample, so the runs don't check exactly the same products. The trend is still clear: every fix to the *data* removed mismatches, and none needed a fix to the *formula*.

The unit tests (`NutriScoreCalculatorTest`: 5 real products from grade A to E, plus the protein and not-scored rules) all pass.

**Conclusion: the calculator's tables and rules agree with Open Food Facts.** The remaining mismatches come from the input data or from rules that are out of scope for v1.

---

## What the data filter got wrong, and how it was fixed

`build_formula_check.py` keeps only general foods by excluding OFF categories that use other rules. Test 2a showed that the first filter was too narrow:

| Fix | Evidence | Effect |
|---|---|---|
| Added `en:meats-and-their-products`, `en:meat-preparations`, `en:prepared-meats` | Product `1916287787710`: OFF categories "Meats and their products", "Meat preparations" | Red-meat cases mostly gone (run 2) |
| Added `en:milks` | Product `4056489155041` (Milbona whole milk): categories "Milks", "Whole milks". The 2023 rules score milk as a **drink** | 278,376 → 273,920 general foods; the milk/yogurt-drink pattern is gone (run 3) |
| Added `en:dairy-drinks`, `en:fermented-milk-drinks`, `en:drinkable-yogurts`, `en:creams`, `en:coconut-products` | Guessed tag names, not confirmed | Unknown: a tag that doesn't exist changes nothing |

**Lesson:** the test found errors in the *answer key's selection*, not in the code under test. That's the same lesson as day 3 (the answer key can be wrong), seen from the other side.

---

## Why "set aside fruit/vegetable data"

The fruit/vegetable % in the Open Food Facts export often isn't the value OFF used to compute its grade:

- `00109123`: the export has **no** fruit/vegetable value, but OFF's score (−4) only works out with 5 fruit/vegetable points (more than 80%).
- `0200030414277`: the export says **60.8%** (2 points), but OFF's score (20) only works out with **0** points. OFF probably computed the grade at a different time than the stored estimate.

No choice of field fixes this: the right value just isn't reliably in the export. So for each mismatch, the test retries the calculator with each possible fruit/vegetable point value (0, 1, 2, 5). If one gives exactly OFF's score, the mismatch is classified as **explained by the fruit/vegetable input** (35 cases in run 3). The strict 99.12% is still reported.

**Caveat:** this classification is lenient. Example from run 3: `0060383037642` has every value at 0. The score matches (0), but OFF says **B** and the calculator says **A**. A score of 0 is a B only in the *beverages* algorithm, so this is a drink that slipped through the filter, not a fruit/vegetable problem. It still lands in "explained" because the score matches. So the 35 "explained" cases aren't all fruit/vegetable cases, and the classifier should also require the *grade* to match. That's a small fix for later.

---

## The 9 unexplained mismatches (run 3)

| Product | OFF | Calculator | Likely cause |
|---|---|---|---|
| `2846721024608` | C (5) | A (0) | **Red meat.** 22 g protein; with protein capped at 2: 7 − 2 = 5 ✓ |
| `7804684740183` | C (6) | B (1) | **Red meat.** 20 g protein; with the cap: 8 − 2 = 6 ✓ |
| `0855002003005` | C (3) | B (2) | **Red meat.** With the cap: 5 − 2 = 3 ✓ (already in run 1) |
| `0855002003364` | C (4) | B (2) | **Red meat.** With the cap: 6 − 2 = 4 ✓ (already in run 1) |
| `4056489104254` | D (11) | C (9) | **Coconut product** (61.6 g saturated fat, 100% "fruit"), scored with the fats rules. The `en:coconut-products` guess didn't catch it |
| `0072250017671` | B (1) | A (0) | **Salt exactly 1.4 g**, on a threshold. OFF most likely computed salt from sodium with decimal noise (1.40000001 > 1.4). The calculator follows the spec ("above the threshold") |
| `3023290076989` | C (5) | B (1) | Probably a **drink** (171 kJ, 5.9 g sugars, 3.8 g protein): the dairy-drink pattern again. Not confirmed |
| `5601082042602` | C (4) | A (−1) | **51 g fibre** per 100 g is suspicious. The 5-point gap equals the fibre points exactly, so OFF probably didn't count the fibre (bad value or different category). Not confirmed |
| `4075600113661` | B (1) | A (0) | 1-point gap, cause unknown. Not investigated |

**4 of 9 are red meat that no category tag caught**, probably because those products have no meat category at all in OFF. More tags won't fix that; only implementing the rule would.

---

## What this means for the app

- **The formula is right**, and that's now measured on 5,000 real products instead of claimed: 99.82%, and every remaining gap has a named cause or is a single product.
- **The app only scores `GENERAL` products**, so the drink and fat patterns matter in the app too: a drinkable yogurt typed as `GENERAL` would get the wrong rules. That depends on the AI choosing the right `type` (prompt rule 8), which the day 5 eval should measure.
- **The red meat rule is a known gap.** A meat product photographed in the app will get too good a grade (by up to 5 points). Two options for later: implement the cap with a `RED_MEAT` type, or mark meat products "not scored". To decide when meat products enter the test set.
- **The app works end to end:** product p004 (strawberry jam), photographed, read by `gpt-4.1-mini` and scored by the calculator, gets **D (18)**. That's the same as the expected grade computed from the hand-typed values (`LabelsExpectedGradesTest`).
- **Fruit/vegetable % is never printed on labels**, so the app assumes 0% and says so (`fruitVegAssumedZero: true`). For fruit-based products like jam, the official grade may be better. Test 2b will measure how often this changes the grade on the photographed products.

---

## Next steps

1. Make the "explained by fruit/vegetable" classification also require the grade to match (see the caveat above).
2. Optional: find the real OFF tag for coconut products (check `4056489104254` on openfoodfacts.org).
3. Decide on red meat (cap rule or "not scored") before photographing meat products.