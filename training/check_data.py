"""
Counts how many Open Food Facts products are usable for LabelLens,
and finds which Nutri-Score version the grades use.

Reads the file in batches of 100,000 products, so it works with little RAM.
Takes a few minutes on 4.8 million products.

Usage:  python check_data.py path\to\food.parquet
"""
import sys
from collections import Counter

import pyarrow.parquet as pq

PATH = sys.argv[1] if len(sys.argv) > 1 else "food.parquet"
COLUMNS = [
    "code", "countries_tags", "ingredients_text", "allergens_tags",
    "obsolete", "misc_tags", "nutriscore_grade", "nutriments",
    "nutrition_data_per", "data_quality_errors_tags",
]
NUTRIENTS = {"energy-kj", "sugars", "saturated-fat", "salt", "fiber", "proteins"}
GRADES = {"a", "b", "c", "d", "e"}


def has_ingredients(entries):
    """True if any language version of the ingredients text has 20+ characters."""
    if not entries:
        return False
    return any(e["text"] and len(e["text"].strip()) >= 20 for e in entries)


def has_all_nutrients(nutriments):
    """True if all six Nutri-Score inputs have a per-100g value."""
    if not nutriments:
        return False
    found = {n["name"] for n in nutriments if n["100g"] is not None}
    return NUTRIENTS <= found


counts = Counter()
nutriscore_tags = Counter()
allergen_counts = Counter()
example_nutriments = None

pf = pq.ParquetFile(PATH)
for batch in pf.iter_batches(batch_size=100_000, columns=COLUMNS):
    for p in batch.to_pylist():
        counts["total"] += 1
        if p["obsolete"]:
            continue
        counts["not obsolete"] += 1

        for tag in p["misc_tags"] or []:
            if "nutriscore" in tag:
                nutriscore_tags[tag] += 1

        if p["data_quality_errors_tags"]:
            continue
        counts["no data errors"] += 1

        countries = p["countries_tags"] or []
        is_ma = "en:morocco" in countries
        is_fr = "en:france" in countries

        # Usable for allergen training/testing
        if has_ingredients(p["ingredients_text"]):
            counts["with ingredients"] += 1
            if is_ma:
                counts["with ingredients, Morocco"] += 1
            if is_fr:
                counts["with ingredients, France"] += 1
            for a in p["allergens_tags"] or []:
                allergen_counts[a] += 1
            if not p["allergens_tags"]:
                counts["with ingredients, no allergens"] += 1

        # Usable for the Nutri-Score formula check
        if (p["nutriscore_grade"] in GRADES
                and p["nutrition_data_per"] == "100g"
                and has_all_nutrients(p["nutriments"])):
            counts["usable for Nutri-Score check"] += 1
            if example_nutriments is None:
                example_nutriments = (p["code"], p["nutriscore_grade"], p["nutriments"])

    print(f"... {counts['total']:,} products read", end="\r")

print("\n\n=== Product counts ===")
for name, n in counts.items():
    print(f"{name:40} {n:>10,}")

print("\n=== Nutri-Score tags in misc_tags (top 15) ===")
for tag, n in nutriscore_tags.most_common(15):
    print(f"{tag:50} {n:>10,}")

print("\n=== Most common allergens (top 15) ===")
for tag, n in allergen_counts.most_common(15):
    print(f"{tag:30} {n:>10,}")

if example_nutriments:
    code, grade, nutriments = example_nutriments
    print(f"\n=== Example nutriments: product {code}, grade {grade} ===")
    for n in nutriments:
        print(f"{n['name']:35} 100g={n['100g']}  unit={n['unit']}")
