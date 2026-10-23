"""
Builds the data for Test 2a: 5,000 Open Food Facts products scored with the
GENERAL-FOODS Nutri-Score (2023), with their inputs and OFF's official grade.

Your Java calculator must give the same grade on (almost) all of them.

Usage (from the project folder, with the virtual environment active):
    python training/build_formula_check.py path/to/food.parquet

Output:
    eval-food/data/off/formula_check.jsonl   one product per line
    eval-food/data/off/DATA_CARD.md          where the data comes from, filters, counts
"""
import hashlib
import heapq
import json
import sys
from collections import Counter
from datetime import date
from pathlib import Path

import pyarrow.parquet as pq

PARQUET = Path(sys.argv[1] if len(sys.argv) > 1 else "food.parquet")
OUT_DIR = Path(__file__).resolve().parent.parent / "eval-food" / "data" / "off"
SAMPLE_SIZE = 5000

COLUMNS = [
    "code", "categories_tags", "nutriments", "nutriscore_grade", "nutriscore_score",
    "nutrition_data_per", "data_quality_errors_tags", "obsolete",
]

# v1 only scores general foods. Products in these categories use other Nutri-Score
# rules (beverages, cheese, fats/oils/nuts/seeds) or a special rule (red meat: protein
# points capped), so they're excluded from the formula check.
EXCLUDED_CATEGORIES = {
    # beverages
    "en:beverages",
    # cheese
    "en:cheeses",
    # fats, oils, nuts, seeds
    "en:fats", "en:vegetable-fats", "en:animal-fats", "en:vegetable-oils", "en:olive-oils",
    "en:butters", "en:margarines", "en:nuts", "en:seeds", "en:nut-butters",
    "en:peanut-butters", "en:nuts-and-their-products", "en:oilseed-purees",
    # red meat (special protein rule in 2023)
    "en:meats", "en:meat-based-products",
    # Added after Test 2a (2026-10-23): seen on product 1916287787710, which slipped through.
    "en:meats-and-their-products", "en:meat-preparations", "en:prepared-meats",
    # Added after Test 2a: dairy drinks (scored as beverages) and creams (scored as fats).
    # Unconfirmed tag names: excluding a tag that doesn't exist changes nothing, so it's safe.
    "en:dairy-drinks", "en:fermented-milk-drinks", "en:drinkable-yogurts",
    "en:creams", "en:coconut-products",
    # Confirmed 2026-10-23 on 4056489155041 (Milbona whole milk): Nutri-Score 2023 scores milk as a drink.
    "en:milks"
}

# OFF nutrient name -> our name. All six are required.
REQUIRED = {
    "energy-kj": "energy_kj",
    "sugars": "sugars",
    "saturated-fat": "saturated_fat",
    "salt": "salt",
    "fiber": "fibre",
    "proteins": "protein",
}
GRADES = {"a", "b", "c", "d", "e"}


def per_100g(nutriments):
    """nutriments is a list of {name, 100g, ...}: turn it into {name: value per 100 g}."""
    return {n["name"]: n["100g"] for n in nutriments or [] if n.get("100g") is not None}


def fruit_veg_values(values):
    """Every fruit/vegetable field OFF has for this product. Kept as-is: which one OFF's
    Nutri-Score used is one of the things Test 2a mismatches will tell you."""
    return {k: v for k, v in values.items() if k.startswith("fruits-vegetables")}


def clean(value):
    """The Parquet file stores 32-bit floats: 0.2 comes back as 0.20000000298.
    Right on a Nutri-Score threshold, that would add a point the product doesn't have.
    Rounding to 3 decimals gives back the value that was typed."""
    return round(float(value), 3)


def sort_key(code):
    """Deterministic 'random' order: rerunning gives the same 5,000 products."""
    return int(hashlib.md5(code.encode("utf-8")).hexdigest(), 16)


def main():
    counts = Counter()
    # heapq is a min-heap. Storing -key keeps the LARGEST key at the top, so we can
    # throw it out whenever a smaller one arrives: in the end we keep the smallest keys.
    best = []  # entries: (-key, code, record)

    pf = pq.ParquetFile(PARQUET)
    for batch in pf.iter_batches(batch_size=100_000, columns=COLUMNS):
        for p in batch.to_pylist():
            counts["1. all products"] += 1
            if p["obsolete"] or p["data_quality_errors_tags"]:
                continue
            counts["2. not obsolete, no data-quality errors"] += 1
            if p["nutriscore_grade"] not in GRADES or p["nutriscore_score"] is None:
                continue
            counts["3. with a grade A-E"] += 1
            if p["nutrition_data_per"] != "100g":
                continue
            counts["4. values per 100 g"] += 1
            categories = set(p["categories_tags"] or [])
            if categories & EXCLUDED_CATEGORIES:
                continue
            counts["5. general foods only"] += 1
            values = per_100g(p["nutriments"])
            if not all(name in values for name in REQUIRED):
                continue
            counts["6. all six inputs present"] += 1

            record = {
                "code": p["code"],
                **{ours: clean(values[off]) for off, ours in REQUIRED.items()},
                "fruit_veg": {k: clean(v) for k, v in fruit_veg_values(values).items()},
                "expected_score": p["nutriscore_score"],
                "expected_grade": p["nutriscore_grade"].upper(),
            }
            key = sort_key(p["code"])
            if len(best) < SAMPLE_SIZE:
                heapq.heappush(best, (-key, p["code"], record))
            elif key < -best[0][0]:
                heapq.heapreplace(best, (-key, p["code"], record))
        print(f"... {counts['1. all products']:,} products read", end="\r")

    sample = sorted((r for _, _, r in best), key=lambda r: r["code"])
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUT_DIR / "formula_check.jsonl", "w", encoding="utf-8") as f:
        for r in sample:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    grades = Counter(r["expected_grade"] for r in sample)
    write_data_card(counts, len(sample), grades)

    print("\n")
    for name, n in sorted(counts.items()):
        print(f"{name:45} {n:>10,}")
    print(f"\nSample written: {len(sample):,} products -> {OUT_DIR / 'formula_check.jsonl'}")
    print("Grades in sample:", dict(sorted(grades.items())))



def write_data_card(counts, sample_size, grades):
    lines = [
        "# Data card: formula_check.jsonl",
        "",
        f"**Built:** {date.today().isoformat()} by `training/build_formula_check.py`",
        f"**Source:** Open Food Facts product export (`food.parquet`), file `{PARQUET.name}`",
        "**Licence:** Open Database License (ODbL). Contains data from Open Food Facts "
        "(https://world.openfoodfacts.org). This file is an adapted database and is shared under the ODbL.",
        "",
        "## Purpose",
        "Test 2a: check the Java Nutri-Score calculator (2023, general foods) against Open Food Facts' grades.",
        "",
        "## Filters (products left after each step)",
        "",
        "| Step | Products |",
        "|---|---|",
        *[f"| {name} | {n:,} |" for name, n in sorted(counts.items())],
        "",
        f"Excluded categories (other Nutri-Score rules or special cases): {', '.join(sorted(EXCLUDED_CATEGORIES))}.",
        "",
        "## Sample",
        f"{sample_size:,} products, chosen deterministically (smallest MD5 hash of the barcode), "
        "so rerunning the script gives the same file.",
        "",
        "Grades: " + ", ".join(f"{g}: {n:,}" for g, n in sorted(grades.items())),
        "",
        "## Fields",
        "- `energy_kj`, `sugars`, `saturated_fat`, `salt`, `fibre`, `protein`: per 100 g, from OFF `nutriments`.",
        "- `fruit_veg`: every `fruits-vegetables-*` value OFF has (often estimated from ingredients, sometimes missing).",
        "- `expected_score`, `expected_grade`: OFF's Nutri-Score (2023).",
        "",
        "## Known issues",
        "- Category exclusion relies on OFF categories, which volunteers fill in: some beverages, cheeses "
        "or fats may have no category and slip through. They'll show up as mismatches.",
        "- Some values are fake zeros (\"not filled in\" typed as 0).",
        "- The fruit/vegetable percentage is usually estimated by OFF from the ingredients.",
    ]
    (OUT_DIR / "DATA_CARD.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
