# Allergen data

Built 2026-10-27 by `training/build_allergen_sets.py` from the Open Food Facts Parquet export.
Licence: Open Food Facts data, ODbL. Credit: Open Food Facts contributors.

## Filters (product counts)

    0. all products                                       4,792,125
    1. Moroccan, ingredients text, no data errors             5,931
    2. after junk and photographed products                   5,800
       removed: photographed product                              8
       removed: fake barcode                                      6
       fr: HTML removed                                         927
       removed: non-food category                               117
       ar: removed, text not really in ar                       117
       fr: removed, text not really in fr                       356
       ar: HTML removed                                           3
       fr: duplicate text removed                               140
    3. fr: usable after duplicates                            3,007
       ar: duplicate text removed                                 2
    3. ar: usable after duplicates                              339

## Files

- `eval-food/data/off/allergen_test.jsonl`: 500 products (French text). Test 3.
- `eval-food/data/off/allergen_test_ar.jsonl`: 226 products (Arabic text), none in the training set.
- `training/data/allergen_train.jsonl`: 2507 products (French text). Fine-tuning only, never tested on.

Each line: code, lang, text (full cleaned text, may include a "may contain" sentence),
allergens and traces (EU-14 tags from Open Food Facts), had_html.

## Allergens in the test set

| Allergen | Test (fr) | Test (ar) | Training |
|---|---|---|---|
| en:milk | 190 | 85 | 1019 |
| en:gluten | 134 | 24 | 720 |
| en:soybeans | 125 | 29 | 606 |
| en:eggs | 33 | 3 | 203 |
| en:nuts | 73 | 13 | 278 |
| en:mustard | 17 | 1 | 85 |
| en:fish | 25 | 0 | 76 |
| en:peanuts | 13 | 3 | 75 |
| en:celery | 10 | 0 | 13 |
| en:sesame-seeds | 8 (too few to measure) | 1 | 48 |
| en:sulphur-dioxide-and-sulphites | 5 (too few to measure) | 1 | 48 |
| en:crustaceans | 1 (too few to measure) | 0 | 1 |
| en:molluscs | 1 (too few to measure) | 0 | 1 |
| en:lupin | 2 (too few to measure) | 0 | 7 |

Test set: 167 products with no EU allergen, 159 with traces, 142 had HTML allergen markup (removed).

## Known problems

- The answer key comes from Open Food Facts volunteers and automatic detection; no column says which.
  Missed allergens exist (e.g. a cream "de lait de vache" tagged with none). Disagreements must be checked by hand.
- Traces sometimes repeat the allergens themselves.
- Non-food and fake-barcode filters are heuristics.
