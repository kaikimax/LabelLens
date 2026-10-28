# Day 6: What the allergen data really looks like

**Date:** 2026-10-26 · **Script:** `training/explore_allergens.py` · **Source:** `food.parquet` (Open Food Facts, 4,792,125 products)

Explored **before** building the test set, because day 4 and day 5 both showed the answer key can be wrong.

---

## Numbers

| | Morocco | France |
|---|---|---|
| Products with an ingredients text (20+ characters, no data errors) | **5,931** | 323,248 |
| … with no EU allergen | 2,414 (41%) | 119,421 (37%) |
| … with "may contain" (traces) tags | **1,589 (27%)** | not counted |
| … with a **French** ingredients text | 3,602 | |
| … with an **Arabic** ingredients text | **463** | |
| Exact duplicate ingredients texts | 162 | |

Languages of Moroccan ingredients texts: main 5,837 · fr 3,602 · **en 2,405** · es 856 · **ar 463** · de 376 · it 325 · nl 312. Many "Moroccan" products are imports with English, Spanish or other texts.

### Each allergen, Moroccan products

| Allergen | Morocco | Expected in a 500-product test set |
|---|---|---|
| Milk | 2,098 | ~175 |
| Gluten | 1,528 | ~130 |
| Soy | 1,352 | ~115 |
| Tree nuts | 605 | ~50 |
| Eggs | 370 | ~30 |
| Peanuts | 175 | ~15 |
| Fish | 173 | ~15 |
| Mustard | 138 | ~12 |
| Sesame | 89 | ~7 |
| Sulphites | 76 | ~6 |
| Celery | 32 | ~3 |
| Lupin | 11 | ~1 |
| Crustaceans | 9 | ~1 |
| Molluscs | 5 | ~0 |

**With a random 500, only about 8 allergens reach 10 examples.** The rest must be reported as "too few to measure" (DATA_PLAN rule), or the test set must deliberately include more products with rare allergens.

---

## Problems found in 8 random examples

| Barcode | What's wrong | Consequence |
|---|---|---|
| `6111242109438` "Crème légère **de lait de vache**…" | `allergens: []`: **milk is missing** | An AI that correctly says "milk" would be scored as a **false alarm**. The key misses a real allergen. |
| `0012345678905` (courgette, egg, mustard) | Fake barcode (`…12345678905`); the text contains **`<span class="allergen">ŒUF</span>`** | Junk entry. And the HTML **marks the allergens inside the input**. |
| `0014113230131` "**`<span class="allergen">`Amandes`</span>`** 100%" | HTML markers again; nuts listed as allergen **and** as trace | Same leak. Traces overlap with allergens. |
| `6118001041268` "orlistat, microcrystalline cellulose…" | A **medicine**, not a food | Junk in the pool. |
| `0011210008328` TABASCO Sriracha | Same product as **p007** (barcode `011210008328`) | Must be removed: a photographed product can't be in the test or training data (DATA_PLAN rule). |

Correct-looking: the wheat-flour biscuit (gluten, milk, soy), the Arabic soft drink (none), the mint sweets (none).

**1 clear key error in 8 is far too few to estimate a rate, but it's the dangerous kind** (a missed allergen in the answer key penalises the AI for being right).

---

## What this changes for the plan

1. **Strip the HTML before anything else.** Some texts mark the allergens with `<span class="allergen">…</span>`. Left in, the input gives away the answer: any method would look perfect on those products. Remove the tags (keep the words), and count how many products had them.
2. **No column says who set a tag.** The file only has `allergens_tags` and `traces_tags`; it can't tell a tag typed by a person from one OFF detected automatically. So the answer key's quality can't be filtered: it has to be **measured**, by checking disagreements by hand (as on days 4 and 5).
3. **Language.** Use the French text when there is one (3,602 Moroccan products). Arabic is only 463 products: enough for a small separate slice, not for a main test set.
4. **Remove junk:** fake barcodes, non-food products (medicines), and duplicates (162).
5. **Remove the photographed products** by barcode (p007 is already in the pool).
6. **Traces are common (27%)** and sometimes repeat the allergens themselves. The "may contain" decision (still open) matters for a quarter of the products.

---

## Found on the first build (2026-10-27): language fields can't be trusted

The first `allergen_test_ar.jsonl` had 304 products, but only **206 were really in Arabic** (80%+ Arabic letters). 55 mixed French and Arabic, 43 had no Arabic at all (e.g. an English biscuit list in the "ar" field). Volunteers paste whatever text they have into whichever field.

Fix in `build_allergen_sets.py`: a text is kept only if it's really in its field's language (Arabic: 80%+ Arabic letters; French: almost no Arabic letters, and more common French words than English ones, e.g. "sucre, farine, lait" vs "sugar, flour, milk"). The removed counts appear in `ALLERGEN_DATA_CARD.md`.

## Decisions (2026-10-26, details in the PLAN.md decisions log)

- **Allergens:** Java word list and AI, combined; each also measured separately.
- **"May contain":** only what's printed, never guessed; Java finds the sentence first.
- **Rare allergens:** "too few to measure" below 10 products in the test set; still counted in the overall score.
- **Cleaning:** strip the HTML allergen markers, remove junk, duplicates and photographed products, prefer French text.
