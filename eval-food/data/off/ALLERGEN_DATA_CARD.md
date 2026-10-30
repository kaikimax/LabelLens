# Allergen data

Built 2026-10-30 by `training/build_allergen_sets.py` from the Open Food Facts Parquet export.
Licence: Open Food Facts data, ODbL. Credit: Open Food Facts contributors.

## Filters (product counts)

    0. all products                                       4,792,125
    1. Moroccan, ingredients text, no data errors             5,931
    2. after junk and photographed products                   5,781
       removed: photographed product                             14
       removed: fake barcode                                      6
       fr: traces dropped from the key, not printed in the text        421
       fr: allergen markup removed                            1,082
       removed: non-food category                               117
       ar: removed, text not really in ar                       117
       fr: removed, text not really in fr                       381
       removed: unusable text (DROP)                             13
       ar: traces dropped from the key, not printed in the text         27
       ar: allergen markup removed                                5
       fr: duplicate text removed                               144
    3. fr: usable after duplicates                            2,962
       ar: duplicate text removed                                 2
    3. ar: usable after duplicates                              339
    4. training: Moroccan products                            2,462
    4. training: topped up with French products                 538
    5. ar: removed, same product in training                    112
    6. answer key fixed by hand (KEY_FIXES)                      64

## Files

- `eval-food/data/off/allergen_test.jsonl`: 500 products (French text). Test 3.
- `eval-food/data/off/allergen_test_ar.jsonl`: 227 products (Arabic text), none in the training set.
- `training/data/allergen_train.jsonl`: 3000 products (French text): 2462 Moroccan, 538 French (top-up, as planned in DATA_PLAN). Fine-tuning only, never tested on.
- Arabic products whose French text is in the training set are left out of the Arabic slice: same product, so testing on it would test on a practice question.

Each line: code, lang, origin, text (full cleaned text, may include a "may contain" sentence),
allergens and traces (EU-14 tags from Open Food Facts; traces only when the text prints a
"may contain"-like statement), traces_off (OFF's original traces), had_markup, and key_fixed
(the reason) when the answer key was corrected by hand.

## Allergens in the test set

| Allergen | Test (fr) | Test (ar) | Training |
|---|---|---|---|
| en:milk | 200 | 86 | 1207 |
| en:gluten | 138 | 24 | 874 |
| en:soybeans | 126 | 29 | 661 |
| en:eggs | 37 | 4 (too few to measure) | 280 |
| en:nuts | 67 | 13 | 317 |
| en:mustard | 16 | 1 (too few to measure) | 109 |
| en:fish | 25 | 0 (too few to measure) | 110 |
| en:peanuts | 11 | 3 (too few to measure) | 84 |
| en:celery | 8 (too few to measure) | 0 (too few to measure) | 34 |
| en:sesame-seeds | 4 (too few to measure) | 1 (too few to measure) | 62 |
| en:sulphur-dioxide-and-sulphites | 11 | 1 (too few to measure) | 62 |
| en:crustaceans | 1 (too few to measure) | 0 (too few to measure) | 10 |
| en:molluscs | 0 (too few to measure) | 0 (too few to measure) | 7 |
| en:lupin | 0 (too few to measure) | 0 (too few to measure) | 9 |

Test set: 157 products with no EU allergen, 98 with traces, 179 had allergen markup (HTML or _underscores_, removed), 61 with a hand-fixed answer key.

## Known problems

- The answer key comes from Open Food Facts volunteers and automatic detection; no column says which.
  Missed allergens exist (e.g. a cream "de lait de vache" tagged with none). Disagreements must be checked by hand.
- Traces sometimes repeat the allergens themselves.
- Only the reviewed products are fixed (KEY_FIXES): the rest of the key is still unchecked.
  Day 6 reviews: 4 of 14 (round 1), then 67 of the 89 products the best setup got wrong had a key error or an unusable text (round 2).
  Only disagreements were reviewed, so errors the key shares with every setup remain.
- Non-food and fake-barcode filters are heuristics.
