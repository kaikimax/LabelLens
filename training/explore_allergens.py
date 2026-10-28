"""
Day 6, before building anything: what does the allergen data in food.parquet really look like?

Answers the questions the test set depends on:
  - how many Moroccan products have an ingredients text, and in which languages;
  - how often each of the 14 EU allergens appears (enough to measure each one?);
  - how many products have "may contain" (traces) tags;
  - how many ingredients texts are exact duplicates (an exam question copied into practice);
  - which allergen-related columns exist (OFF computes some tags automatically from the
    ingredients: if the answer key is itself produced by a word list, that changes what
    the eval measures).

Usage (from the project folder, virtual environment active):
    python training/explore_allergens.py food.parquet
Reading the whole file takes a few minutes.
"""
import random
import sys
from collections import Counter
from pathlib import Path

import pyarrow.parquet as pq

PARQUET = Path(sys.argv[1] if len(sys.argv) > 1 else "food.parquet")

EU14 = [
    "en:milk", "en:gluten", "en:soybeans", "en:eggs", "en:nuts", "en:mustard", "en:fish",
    "en:peanuts", "en:celery", "en:sesame-seeds", "en:sulphur-dioxide-and-sulphites",
    "en:crustaceans", "en:molluscs", "en:lupin",
]
WANTED = ["code", "countries_tags", "ingredients_text", "allergens_tags", "traces_tags",
          "obsolete", "data_quality_errors_tags"]


def texts(entries):
    """ingredients_text is a list of {lang, text}: return {lang: text} for texts of 20+ characters."""
    return {e["lang"]: e["text"].strip() for e in entries or []
            if e.get("text") and len(e["text"].strip()) >= 20}


def main():
    pf = pq.ParquetFile(PARQUET)
    names = pf.schema_arrow.names
    print("=== Allergen-related columns in the file ===")
    related = [n for n in names if "allerg" in n.lower() or "trace" in n.lower()]
    print("  " + (", ".join(related) if related else "(none besides the ones below)"))
    columns = [c for c in WANTED if c in names]
    missing = [c for c in WANTED if c not in names]
    if missing:
        print(f"  MISSING expected columns: {missing}")

    counts = Counter()
    langs = Counter()
    allergens = {"morocco": Counter(), "france": Counter()}
    traces = Counter()
    seen_texts = Counter()
    examples = []
    rng = random.Random(42)

    for batch in pf.iter_batches(batch_size=100_000, columns=columns):
        for p in batch.to_pylist():
            counts["all products"] += 1
            if p.get("obsolete") or p.get("data_quality_errors_tags"):
                continue
            t = texts(p.get("ingredients_text"))
            if not t:
                continue
            countries = set(p.get("countries_tags") or [])
            where = "morocco" if "en:morocco" in countries else "france" if "en:france" in countries else None
            if where is None:
                continue
            counts[f"{where}: with ingredients"] += 1
            tags = [a for a in (p.get("allergens_tags") or []) if a in EU14]
            trace_tags = [a for a in (p.get("traces_tags") or []) if a in EU14]
            for a in tags:
                allergens[where][a] += 1
            if not tags:
                counts[f"{where}: no EU allergen"] += 1
            if where != "morocco":
                continue

            # Moroccan products: the future test set. Look closer.
            for lang in t:
                langs[lang] += 1
            if "fr" in t:
                counts["morocco: French text available"] += 1
            if "ar" in t:
                counts["morocco: Arabic text available"] += 1
            if trace_tags:
                counts["morocco: with traces (may contain)"] += 1
                for a in trace_tags:
                    traces[a] += 1
            main_text = t.get("fr") or t.get("main") or next(iter(t.values()))
            seen_texts[main_text.lower()] += 1
            if len(examples) < 8 or rng.random() < 0.002:
                if len(examples) >= 8:
                    examples[rng.randrange(8)] = (p["code"], main_text, tags, trace_tags)
                else:
                    examples.append((p["code"], main_text, tags, trace_tags))
        print(f"... {counts['all products']:,} products read", end="\r")

    print("\n\n=== Counts ===")
    for k, v in sorted(counts.items()):
        print(f"  {k:<42} {v:>10,}")

    duplicates = sum(n - 1 for n in seen_texts.values() if n > 1)
    print(f"  {'morocco: duplicate ingredients texts':<42} {duplicates:>10,}")

    print("\n=== Languages of Moroccan ingredients texts ===")
    for lang, n in langs.most_common(8):
        print(f"  {lang:<8} {n:>8,}")

    print("\n=== EU allergens: Morocco (test set pool) vs France (training top-up) ===")
    for a in EU14:
        m, f = allergens["morocco"][a], allergens["france"][a]
        flag = "   <- fewer than 10 in Morocco" if m < 10 else ""
        print(f"  {a:<38} {m:>7,} {f:>10,}{flag}")

    print("\n=== Traces (may contain), Morocco ===")
    for a, n in traces.most_common():
        print(f"  {a:<38} {n:>7,}")

    print("\n=== 8 random Moroccan examples (check: do the tags match the text?) ===")
    for code, text, tags, trace_tags in examples:
        print(f"\n  {code}")
        print(f"    text:      {text[:220]}{'...' if len(text) > 220 else ''}")
        print(f"    allergens: {tags}")
        print(f"    traces:    {trace_tags}")


if __name__ == "__main__":
    main()
