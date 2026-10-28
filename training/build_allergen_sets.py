"""
Day 6: builds the allergen data from food.parquet, following the decisions log (2026-10-26)
and the findings in docs/notes/day6-allergen-data.md.

    Test set (Test 3)   500 Moroccan products with a French ingredients text
    Arabic slice        Moroccan products with an Arabic text, not in the training set
    Training set        3,000 other Moroccan products (fine-tuning, days 7-8). NEVER used for testing.

Every product keeps its FULL cleaned text (ingredients + any "may contain" sentence):
splitting off the "may contain" part is the app's job (Java), and the eval must test that too.

Cleaning, in order:
  1. Not obsolete, no data-quality errors, ingredients text of 20+ characters.
  2. Not a fake barcode (e.g. ...12345678905) or a non-food product.
  3. Not one of the photographed products (labels.json), compared without leading zeros.
  4. HTML removed: some texts mark the allergens with <span class="allergen">...</span>,
     which would give the answer away. The words are kept, the markup is removed.
  5. Exact duplicate texts removed (case and spacing ignored), so a test question can't
     reappear in the training set under another barcode.
Groups are decided by a hash of the barcode: rerunning gives exactly the same files.

Usage (from the project folder, .venv active):
    python training/build_allergen_sets.py food.parquet
"""
import hashlib
import heapq
import html
import json
import re
import sys
from collections import Counter
from datetime import date
from pathlib import Path

import pyarrow.parquet as pq

PARQUET = Path(sys.argv[1] if len(sys.argv) > 1 else "food.parquet")
ROOT = Path(__file__).resolve().parent.parent
LABELS = ROOT / "eval-food" / "data" / "photos" / "labels.json"
TEST_DIR = ROOT / "eval-food" / "data" / "off"
TRAIN_DIR = ROOT / "training" / "data"

TEST_SIZE = 500
TRAIN_SIZE = 3000
FRANCE_POOL = 5000   # French products kept in memory for the training top-up
MIN_TO_MEASURE = 10

EU14 = [
    "en:milk", "en:gluten", "en:soybeans", "en:eggs", "en:nuts", "en:mustard", "en:fish",
    "en:peanuts", "en:celery", "en:sesame-seeds", "en:sulphur-dioxide-and-sulphites",
    "en:crustaceans", "en:molluscs", "en:lupin",
]
COLUMNS = ["code", "countries_tags", "categories_tags", "ingredients_text", "allergens_tags",
           "traces_tags", "obsolete", "data_quality_errors_tags"]

# Unconfirmed tag names: excluding a tag that doesn't exist changes nothing.
NON_FOOD = {"en:non-food-products", "en:dietary-supplements", "en:food-supplements",
            "en:medicines", "en:drugs", "en:cosmetics", "en:pet-food"}

TAG = re.compile(r"<[^>]+>")
ARABIC_LETTER = re.compile(r"[\u0600-\u06FF]")
LATIN_LETTER = re.compile(r"[A-Za-z\u00C0-\u00FF\u0152\u0153]")
WORD = re.compile(r"[a-z\u00e0-\u00ff\u0153]+")
# A few very common words in ingredients lists, to tell French from English.
FRENCH_WORDS = {"et", "de", "du", "des", "sucre", "farine", "lait", "eau", "sel", "huile", "blé", "ou", "arôme", "arômes"}
ENGLISH_WORDS = {"and", "of", "sugar", "flour", "milk", "water", "salt", "oil", "wheat", "or", "flavour", "flavor"}
SPACES = re.compile(r"\s+")


def clean(text):
    """Removes HTML markup and entities, keeps the words. Returns (clean text, had_html)."""
    had_html = "<" in text and TAG.search(text) is not None
    text = html.unescape(TAG.sub("", text))
    return SPACES.sub(" ", text).strip(), had_html


def right_language(text, lang):
    """Open Food Facts fields are filled by volunteers: the "ar" field sometimes holds French,
    the "fr" field English (checked on the first build: 98 of 304 "Arabic" texts were mixed or not
    Arabic at all). Keep a text only if it's really in the language of its field."""
    arabic = len(ARABIC_LETTER.findall(text))
    latin = len(LATIN_LETTER.findall(text))
    if arabic + latin == 0:
        return False
    if lang == "ar":
        return arabic / (arabic + latin) >= 0.8
    if arabic / (arabic + latin) > 0.2:
        return False
    words = WORD.findall(text.lower())
    french = sum(w in FRENCH_WORDS for w in words)
    english = sum(w in ENGLISH_WORDS for w in words)
    return french >= english


def fake_barcode(code):
    digits = code.lstrip("0")
    return (len(digits) < 6
            or "123456789" in code
            or len(set(digits)) <= 2)  # 1111111, 1212121...


def photographed_barcodes():
    if not LABELS.exists():
        return set()
    data = json.loads(LABELS.read_text(encoding="utf-8"))
    return {p["barcode"].lstrip("0") for p in data["products"] if p.get("barcode")}


def sort_key(code):
    """Deterministic 'random' order from the barcode."""
    return hashlib.md5(code.encode("utf-8")).hexdigest()


def texts_by_language(entries):
    return {e["lang"]: e["text"] for e in entries or [] if e.get("text") and len(e["text"].strip()) >= 20}


def main():
    excluded_photos = photographed_barcodes()
    counts = Counter()
    french = []   # (key, record) Moroccan products with a French text
    arabic = []   # (key, record) Moroccan products with an Arabic text
    france_pool = []  # heap of (-key, code, record): French products, for the training top-up

    pf = pq.ParquetFile(PARQUET)
    for batch in pf.iter_batches(batch_size=100_000, columns=COLUMNS):
        for p in batch.to_pylist():
            counts["0. all products"] += 1
            countries = p["countries_tags"] or []
            if "en:morocco" in countries:
                origin = "morocco"
            elif "en:france" in countries:
                origin = "france"
            else:
                continue
            if p["obsolete"] or p["data_quality_errors_tags"]:
                continue
            texts = texts_by_language(p["ingredients_text"])
            if not texts:
                continue
            code = p["code"]
            if origin == "france":
                collect_france(p, texts, code, excluded_photos, france_pool)
                continue
            counts["1. Moroccan, ingredients text, no data errors"] += 1
            if fake_barcode(code):
                counts["   removed: fake barcode"] += 1
                continue
            if set(p["categories_tags"] or []) & NON_FOOD:
                counts["   removed: non-food category"] += 1
                continue
            if code.lstrip("0") in excluded_photos:
                counts["   removed: photographed product"] += 1
                continue
            counts["2. after junk and photographed products"] += 1

            allergens = sorted(a for a in (p["allergens_tags"] or []) if a in EU14)
            traces = sorted(a for a in (p["traces_tags"] or []) if a in EU14)
            for lang, bucket in (("fr", french), ("ar", arabic)):
                if lang not in texts:
                    continue
                text, had_html = clean(texts[lang])
                if len(text) < 20:
                    continue
                if not right_language(text, lang):
                    counts[f"   {lang}: removed, text not really in {lang}"] += 1
                    continue
                if had_html:
                    counts[f"   {lang}: HTML removed"] += 1
                bucket.append((sort_key(code), {
                    "code": code, "lang": lang, "origin": "morocco", "text": text,
                    "allergens": allergens, "traces": traces, "had_html": had_html,
                }))
        print(f"... {counts['0. all products']:,} products read", end="\r")
    print()

    french = dedupe(sorted(french, key=lambda x: x[0]), counts, "fr")
    arabic = dedupe(sorted(arabic, key=lambda x: x[0]), counts, "ar")

    test = french[:TEST_SIZE]
    train = french[TEST_SIZE:TEST_SIZE + TRAIN_SIZE]
    counts["4. training: Moroccan products"] = len(train)
    train += top_up(train, test, france_pool, counts)
    train_codes = {r["code"] for r in train}
    arabic_slice = [r for r in arabic if r["code"] not in train_codes]
    counts["5. ar: removed, same product in training"] = len(arabic) - len(arabic_slice)

    TEST_DIR.mkdir(parents=True, exist_ok=True)
    TRAIN_DIR.mkdir(parents=True, exist_ok=True)
    write(TEST_DIR / "allergen_test.jsonl", test)
    write(TEST_DIR / "allergen_test_ar.jsonl", arabic_slice)
    write(TRAIN_DIR / "allergen_train.jsonl", train)

    report = summary(counts, test, arabic_slice, train)
    print(report)
    (TEST_DIR / "ALLERGEN_DATA_CARD.md").write_text(report, encoding="utf-8")
    if len(train) < TRAIN_SIZE:
        print(f"\nWARNING: only {len(train)} training products (wanted {TRAIN_SIZE}).")


def collect_france(p, texts, code, excluded_photos, pool):
    """Keeps the FRANCE_POOL French products with the smallest barcode hash (deterministic),
    after the same cleaning as the Moroccan ones. Used only to top up the training set."""
    if "fr" not in texts or fake_barcode(code) or code.lstrip("0") in excluded_photos:
        return
    if set(p["categories_tags"] or []) & NON_FOOD:
        return
    key = negate(sort_key(code))
    if len(pool) >= FRANCE_POOL and key <= pool[0][0]:
        return  # pool full and this barcode sorts after all of them: skip the cleaning work
    text, had_html = clean(texts["fr"])
    if len(text) < 20 or not right_language(text, "fr"):
        return
    record = {
        "code": code, "lang": "fr", "origin": "france", "text": text,
        "allergens": sorted(a for a in (p["allergens_tags"] or []) if a in EU14),
        "traces": sorted(a for a in (p["traces_tags"] or []) if a in EU14),
        "had_html": had_html,
    }
    # Max-heap on the key (stored negated): the largest key is dropped when a smaller one arrives.
    entry = (key, code, record)
    if len(pool) < FRANCE_POOL:
        heapq.heappush(pool, entry)
    elif entry > pool[0]:
        heapq.heapreplace(pool, entry)


def negate(hex_key):
    """md5 hex string -> negative int, so heapq (a min-heap) keeps the SMALLEST keys."""
    return -int(hex_key, 16)


def top_up(train, test, pool, counts):
    """Adds French products to the training set until it has TRAIN_SIZE, skipping any text
    already in the test or training set (a duplicate would leak a test question)."""
    needed = TRAIN_SIZE - len(train)
    if needed <= 0:
        return []
    seen = {SPACES.sub(" ", r["text"].lower()).strip() for r in train + test}
    added = []
    for _, _, r in sorted(pool, key=lambda e: -e[0]):
        if len(added) == needed:
            break
        key = SPACES.sub(" ", r["text"].lower()).strip()
        if key in seen:
            continue
        seen.add(key)
        added.append(r)
    counts["4. training: topped up with French products"] = len(added)
    return added


def dedupe(entries, counts, lang):
    seen, kept = set(), []
    for _, r in entries:
        key = SPACES.sub(" ", r["text"].lower()).strip()
        if key in seen:
            counts[f"   {lang}: duplicate text removed"] += 1
            continue
        seen.add(key)
        kept.append(r)
    counts[f"3. {lang}: usable after duplicates"] = len(kept)
    return kept


def write(path, records):
    with open(path, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def few(n):
    return f"{n} (too few to measure)" if n < MIN_TO_MEASURE else str(n)


def per_allergen(records):
    c = Counter(a for r in records for a in r["allergens"])
    return [(a, c[a]) for a in EU14]


def summary(counts, test, arabic_slice, train):
    lines = [
        "# Allergen data",
        "",
        f"Built {date.today()} by `training/build_allergen_sets.py` from the Open Food Facts Parquet export.",
        "Licence: Open Food Facts data, ODbL. Credit: Open Food Facts contributors.",
        "",
        "## Filters (product counts)",
        "",
    ]
    lines += [f"    {k:<52} {v:>10,}" for k, v in counts.items()]
    lines += [
        "",
        "## Files",
        "",
        f"- `eval-food/data/off/allergen_test.jsonl`: {len(test)} products (French text). Test 3.",
        f"- `eval-food/data/off/allergen_test_ar.jsonl`: {len(arabic_slice)} products (Arabic text), none in the training set.",
        f"- `training/data/allergen_train.jsonl`: {len(train)} products (French text): "
        f"{sum(r['origin'] == 'morocco' for r in train)} Moroccan, {sum(r['origin'] == 'france' for r in train)} French "
        "(top-up, as planned in DATA_PLAN). Fine-tuning only, never tested on.",
        "- Arabic products whose French text is in the training set are left out of the Arabic slice: "
        "same product, so testing on it would test on a practice question.",
        "",
        "Each line: code, lang, origin, text (full cleaned text, may include a \"may contain\" sentence),",
        "allergens and traces (EU-14 tags from Open Food Facts), had_html.",
        "",
        "## Allergens in the test set",
        "",
        "| Allergen | Test (fr) | Test (ar) | Training |",
        "|---|---|---|---|",
    ]
    ar = dict(per_allergen(arabic_slice))
    tr = dict(per_allergen(train))
    for a, n in per_allergen(test):
        lines.append(f"| {a} | {few(n)} | {few(ar[a])} | {tr[a]} |")
    no_allergen = sum(1 for r in test if not r["allergens"])
    with_traces = sum(1 for r in test if r["traces"])
    with_html = sum(1 for r in test if r["had_html"])
    lines += [
        "",
        f"Test set: {no_allergen} products with no EU allergen, {with_traces} with traces, "
        f"{with_html} had HTML allergen markup (removed).",
        "",
        "## Known problems",
        "",
        "- The answer key comes from Open Food Facts volunteers and automatic detection; no column says which.",
        "  Missed allergens exist (e.g. a cream \"de lait de vache\" tagged with none). Disagreements must be checked by hand.",
        "- Traces sometimes repeat the allergens themselves.",
        "- Non-food and fake-barcode filters are heuristics.",
    ]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    main()
