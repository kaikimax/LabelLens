# Allergen data

Built 2026-10-29 by `training/build_allergen_sets.py` from the Open Food Facts Parquet export.
Licence: Open Food Facts data, ODbL. Credit: Open Food Facts contributors.

## Filters (product counts)

    0. all products                                       4,792,125
    1. Moroccan, ingredients text, no data errors             5,931
    2. after junk and photographed products                   5,793
       removed: photographed product                             14
       removed: fake barcode                                      6
       fr: traces dropped from the key, not printed in the text        405
       fr: allergen markup removed                            1,083
       removed: non-food category                               117
       ar: removed, text not really in ar                       117
       fr: removed, text not really in fr                       355
       ar: traces dropped from the key, not printed in the text         27
       ar: allergen markup removed                                5
       removed: unusable text (DROP)                              1
       fr: duplicate text removed                               144
    3. fr: usable after duplicates                            3,000
       ar: duplicate text removed                                 2
    3. ar: usable after duplicates                              339
    4. training: Moroccan products                            2,500
    4. training: topped up with French products                 500
    5. ar: removed, same product in training                    113
    6. answer key fixed by hand (KEY_FIXES)                       6

## Files

- `eval-food/data/off/allergen_test.jsonl`: 500 products (French text). Test 3.
- `eval-food/data/off/allergen_test_ar.jsonl`: 226 products (Arabic text), none in the training set.
- `training/data/allergen_train.jsonl`: 3000 products (French text): 2500 Moroccan, 500 French (top-up, as planned in DATA_PLAN). Fine-tuning only, never tested on.
- Arabic products whose French text is in the training set are left out of the Arabic slice: same product, so testing on it would test on a practice question.

Each line: code, lang, origin, text (full cleaned text, may include a "may contain" sentence),
allergens and traces (EU-14 tags from Open Food Facts; traces only when the text prints a
"may contain"-like statement), traces_off (OFF's original traces), had_markup, and key_fixed
(the reason) when the answer key was corrected by hand.

## Allergens in the test set

| Allergen | Test (fr) | Test (ar) | Training |
|---|---|---|---|
| en:milk | 189 | 85 | 1207 |
| en:gluten | 131 | 24 | 876 |
| en:soybeans | 125 | 29 | 666 |
| en:eggs | 33 | 3 (too few to measure) | 282 |
| en:nuts | 74 | 13 | 318 |
| en:mustard | 17 | 1 (too few to measure) | 109 |
| en:fish | 25 | 0 (too few to measure) | 109 |
| en:peanuts | 13 | 3 (too few to measure) | 85 |
| en:celery | 10 | 0 (too few to measure) | 34 |
| en:sesame-seeds | 8 (too few to measure) | 1 (too few to measure) | 61 |
| en:sulphur-dioxide-and-sulphites | 5 (too few to measure) | 1 (too few to measure) | 64 |
| en:crustaceans | 1 (too few to measure) | 0 (too few to measure) | 10 |
| en:molluscs | 1 (too few to measure) | 0 (too few to measure) | 7 |
| en:lupin | 2 (too few to measure) | 0 (too few to measure) | 9 |

Test set: 171 products with no EU allergen, 96 with traces, 174 had allergen markup (HTML or _underscores_, removed), 6 with a hand-fixed answer key.

## Known problems

- The answer key comes from Open Food Facts volunteers and automatic detection; no column says which.
  Missed allergens exist (e.g. a cream "de lait de vache" tagged with none). Disagreements must be checked by hand.
- Traces sometimes repeat the allergens themselves.
- Only the reviewed products are fixed (KEY_FIXES): the rest of the key is still unchecked.
  Day 6 review: 4 of 14 products checked had a wrong key.
- Non-food and fake-barcode filters are heuristics.
