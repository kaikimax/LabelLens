# Data card: formula_check.jsonl

**Built:** 2026-10-23 by `training/build_formula_check.py`
**Source:** Open Food Facts product export (`food.parquet`), file `food.parquet`
**Licence:** Open Database License (ODbL). Contains data from Open Food Facts (https://world.openfoodfacts.org). This file is an adapted database and is shared under the ODbL.

## Purpose
Test 2a: check the Java Nutri-Score calculator (2023, general foods) against Open Food Facts' grades.

## Filters (products left after each step)

| Step | Products |
|---|---|
| 1. all products | 4,792,125 |
| 2. not obsolete, no data-quality errors | 4,609,648 |
| 3. with a grade A-E | 1,343,632 |
| 4. values per 100 g | 375,856 |
| 5. general foods only | 273,920 |
| 6. all six inputs present | 67,210 |

Excluded categories (other Nutri-Score rules or special cases): en:animal-fats, en:beverages, en:butters, en:cheeses, en:coconut-products, en:creams, en:dairy-drinks, en:drinkable-yogurts, en:fats, en:fermented-milk-drinks, en:margarines, en:meat-based-products, en:meat-preparations, en:meats, en:meats-and-their-products, en:milks, en:nut-butters, en:nuts, en:nuts-and-their-products, en:oilseed-purees, en:olive-oils, en:peanut-butters, en:prepared-meats, en:seeds, en:vegetable-fats, en:vegetable-oils.

## Sample
5,000 products, chosen deterministically (smallest MD5 hash of the barcode), so rerunning the script gives the same file.

Grades: A: 1,110, B: 481, C: 1,210, D: 1,088, E: 1,111

## Fields
- `energy_kj`, `sugars`, `saturated_fat`, `salt`, `fibre`, `protein`: per 100 g, from OFF `nutriments`.
- `fruit_veg`: every `fruits-vegetables-*` value OFF has (often estimated from ingredients, sometimes missing).
- `expected_score`, `expected_grade`: OFF's Nutri-Score (2023).

## Known issues
- Category exclusion relies on OFF categories, which volunteers fill in: some beverages, cheeses or fats may have no category and slip through. They'll show up as mismatches.
- Some values are fake zeros ("not filled in" typed as 0).
- The fruit/vegetable percentage is usually estimated by OFF from the ingredients.
