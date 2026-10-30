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
  4. Allergen markup removed: some texts mark the allergens with <span class="allergen">...</span>
     or with underscores (_gluten_), which would give the answer away. Real labels use bold
     type, not markup. The words are kept, the markup is removed (underscores: day 6 review).
  5. Products in DROP removed (found unusable in the hand review, e.g. OCR garbage).
  6. Exact duplicate texts removed (case and spacing ignored), so a test question can't
     reappear in the training set under another barcode.

Answer key, after cleaning (decisions log, 2026-10-29):
  - Traces are kept only if the text prints a "may contain"-like statement (TRACES_PRINTED,
    deliberately broader than the app's splitter so its misses still count). OFF's traces field
    is also filled from the rest of the packaging, which we never send. The original is kept in
    "traces_off".
  - KEY_FIXES: answer-key errors confirmed by reading the text, with the reason. Never edit the
    JSONL by hand: a rebuild would undo it silently.
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
UNDERSCORED = re.compile(r"_([^_\n]{1,80})_")   # _gluten_, _Sardines_
# Any sign of a "may contain" statement, in French, English or Arabic. Broader than the app's
# MayContainSplitter on purpose: a wording the splitter misses must still count as a miss.
# Not "contient/contains" or a bare "présence": those also start ingredient statements
# ("contient du céleri", "Présence de gluten"), which kept wrong traces in the key (review round 2).
TRACES_PRINTED = re.compile(r"trace|contenir|may (?:also )?contain|pr[ée]sence (?:[ée]ventuelle|possible)"
                            r"|atelier|usine|factory|equipment"
                            r"|يحتوي|يحتوى|تحتوي|آثار", re.IGNORECASE)

# Answer-key errors confirmed by reading the text (docs/notes/day6-allergen-eval.md, review rounds
# 1 and 2). Only the fields given are replaced. Round 2 reviewed only the products a setup got
# wrong: products where the key and every setup are wrong together are still unchecked.
A, T = "allergens", "traces"
MILK, GLUTEN, SOY, EGGS, NUTS = "en:milk", "en:gluten", "en:soybeans", "en:eggs", "en:nuts"
MUSTARD, FISH, PEANUTS, CELERY = "en:mustard", "en:fish", "en:peanuts", "en:celery"
SESAME, SULPHITES, LUPIN = "en:sesame-seeds", "en:sulphur-dioxide-and-sulphites", "en:lupin"


def fix(why, allergens=None, traces=None):
    f = {"why": why}
    if allergens is not None:
        f[A] = sorted(allergens)
    if traces is not None:
        f[T] = sorted(traces)
    return f


KEY_FIXES = {
    # ---- Round 1
    "3250390271604": fix("cocoa powder + potassium carbonates: no gluten or milk in the text", allergens=[]),
    "3250390115236": fix("sugar, cocoa, salt, flavouring, vitamin B1: no gluten in the text", allergens=[]),
    "3250390748847": fix("sweeteners, mint flavouring, menthol: no milk in the text", allergens=[]),
    "3222475104336": fix("corn 99.4%, salt: gluten is only printed as a trace", allergens=[]),
    "3270190127765": fix("prints traces of nuts, gluten cereals and milk; the key had no gluten",
                         traces=[GLUTEN, MILK, NUTS]),
    "3222474768461": fix("prints 'Traces de lait, moutarde'; fish is an ingredient, not a trace",
                         traces=[MILK, MUSTARD]),
    # ---- Round 2: allergens the text names but the key misses
    "6111184000541": fix("'Cornichons (dont sulfites)', 'graines de moutarde'", allergens=[MUSTARD, SULPHITES]),
    "6111094000082": fix("durum wheat semolina", allergens=[GLUTEN]),
    "8410376017113": fix("'disulfite de sodium' is a sulphite", allergens=[GLUTEN, MILK, SOY, SULPHITES]),
    "6111265980830": fix("'lecithine de soja'", allergens=[MILK, NUTS, SOY]),
    "6194008552204": fix("'eufs' (OCR of oeufs)", allergens=[EGGS, GLUTEN, MILK, SOY]),
    "6111254878414": fix("'disulfite de potassium' in the mustard", allergens=[EGGS, MUSTARD, SULPHITES]),
    "6111245460345": fix("'farine complète de blé tendre'", allergens=[GLUTEN]),
    "6111246721957": fix("cow's milk; refined soybean fat is exempt (DATA_PLAN)", allergens=[MILK]),
    "6111242100664": fix("'Lait caillé'", allergens=[MILK]),
    "6111214004426": fix("'lécithine de soja'", allergens=[MILK, SOY]),
    "6111242109544": fix("'Lait frais entier, crème', oats", allergens=[GLUTEN, MILK]),
    "3700311810530": fix("'disulfite de sodium'", allergens=[GLUTEN, MILK, SULPHITES]),
    "40338453": fix("'lait', 'lactosérum'", allergens=[GLUTEN, MILK]),
    "6111126005597": fix("'Lait entier', 'crème fraîche'", allergens=[MILK]),
    "6111046002058": fix("'lait frais écrémé', 'crème'", allergens=[MILK]),
    "6111258242556": fix("'ceufs frais' (OCR of oeufs), 'contient ... des ceufs'", allergens=[EGGS, GLUTEN, MILK]),
    "8410376027693": fix("'disulfite de sodium'", allergens=[GLUTEN, MILK, SOY, SULPHITES]),
    "6111249190217": fix("'sardines'", allergens=[FISH]),
    "6111246954614": fix("'Crème de lait de vache'", allergens=[MILK]),
    "6111246250501": fix("'enzymes (sur support blé)'", allergens=[GLUTEN]),
    "6111195018801": fix("no nuts in the text", allergens=[MILK, SOY]),
    # ---- Round 2: allergens the key has but the text doesn't (often traces copied into allergens)
    "3560070330744": fix("lupin, mustard, sesame are only traces", allergens=[GLUTEN, MILK, NUTS, PEANUTS]),
    "8718114734484": fix("no sulphite in the text", allergens=[CELERY, EGGS, MUSTARD]),
    "8410679017155": fix("nuts are only a trace", allergens=[MILK, SOY]),
    "3560070430994": fix("celery is only a trace", allergens=[GLUTEN, MILK, MUSTARD]),
    "8445290728791": fix("nuts are only a trace", allergens=[GLUTEN, MILK]),
    "8410099571794": fix("'lécithine de tournesol': no soy", allergens=[EGGS, GLUTEN, MILK]),
    "8801073113893": fix("noodles: wheat, soy, milk, cheese; nothing else in the text", allergens=[GLUTEN, MILK, SOY]),
    "8712566381340": fix("no soy in the text", allergens=[FISH, GLUTEN, MUSTARD]),
    "3250391737109": fix("lupin, mustard, peanuts, sesame are only traces", allergens=[EGGS, GLUTEN, MILK]),
    "5400141614206": fix("nuts are only a trace (cashew)", allergens=[PEANUTS]),
    "3029330068143": fix("milk, eggs, sesame are only traces", allergens=[GLUTEN]),
    "8008698002223": fix("corn, 'SANS BLÉ': no gluten in the text", allergens=[]),
    # ---- Round 2: both
    "11558813": fix("'mantequilla', 'matière grasse laitière'; traces: gluten, other nuts",
                    allergens=[MILK, NUTS, SOY], traces=[GLUTEN, NUTS]),
    "6111031005385": fix("milk is only 'PEUT CONTENIR DU LAIT'", allergens=[GLUTEN, SOY], traces=[MILK]),
    "4101540705793": fix("'æufs' (OCR); nuts and dairy are traces", allergens=[EGGS, GLUTEN], traces=[MILK, NUTS]),
    "0796554079561": fix("sunflower seeds; traces of wheat, peanuts, sesame, soy",
                         allergens=[], traces=[GLUTEN, PEANUTS, SESAME, SOY]),
    "8410376061772": fix("oats; trace milk only ('Sans fruits à coque')", allergens=[GLUTEN, SOY], traces=[MILK]),
    "8008698005347": fix("no nuts; trace lupin only", allergens=[MILK, SOY], traces=[LUPIN]),
    "8410376013672": fix("'metabisulphite'; traces of milk and soya", allergens=[GLUTEN, SULPHITES],
                         traces=[MILK, SOY]),
    # ---- Round 2: traces the text prints differently
    "8053504210439": fix("prints soy, other nuts, sesame, mustard", traces=[MUSTARD, NUTS, SESAME, SOY]),
    "3222476439505": fix("prints gluten, milk, nuts", traces=[GLUTEN, MILK, NUTS]),
    "3497911101129": fix("no 'may contain'; 'Contient Orge' is an ingredient", traces=[]),
    "87157239": fix("celery is an ingredient ('contient du céleri')", traces=[]),
    "8411037892308": fix("prints 'traces de LAIT' only", traces=[MILK]),
    "3222472669104": fix("prints other nuts and gluten", traces=[GLUTEN, NUTS]),
    "3222475982330": fix("prints soy ('desojaet') and gluten", traces=[GLUTEN, SOY]),
    "4013197767010": fix("prints 'traces de lait' (OCR)", traces=[MILK]),
    "3270190181231": fix("no 'may contain' printed", traces=[]),
    "6111178002292": fix("prints 'présence éventuelle d'autres fruits à coque'", traces=[NUTS]),
    "6194008533920": fix("prints 'noisettes, oeufs'", traces=[EGGS, NUTS]),
    "8076802085738": fix("prints soy and mustard", traces=[MUSTARD, SOY]),
    "3178530407938": fix("prints 'fruits à coque' only", traces=[NUTS]),
    "3560070759354": fix("prints eggs and nuts", traces=[EGGS, NUTS]),
    "3222473950324": fix("prints nuts, milk, soy, lupin, mustard", traces=[LUPIN, MILK, MUSTARD, NUTS, SOY]),
}

# Products whose text can't be used: not an ingredients list, OCR garbage, or not French at all
# although it's in the French field (reviews 1 and 2; the language check below now catches most
# Spanish, German, Italian and Dutch texts too).
DROP = {
    "5999884034469": "OCR garbage: the ingredients can't be read",
    "3073780971638": "OCR garbage, marketing text",
    "3073781141382": "marketing and nutrition text, no ingredients list",
    "8788128769000": "marketing text in French and Spanish, no ingredients list",
    "8713576008821": "Dutch, mojibake Arabic, a few French words",
    "40338576": "Latvian",
    "8436573629850": "Spanish",
    "8410128169916": "Spanish, OCR garbage",
    "8412779240105": "Spanish, no ingredients list",
    "8410376051148": "Spanish",
    "8480012041048": "Spanish",
    "4335619195080": "Spanish and Portuguese",
    "4008400180427": "German",
}
ARABIC_LETTER = re.compile(r"[\u0600-\u06FF]")
LATIN_LETTER = re.compile(r"[A-Za-z\u00C0-\u00FF\u0152\u0153]")
WORD = re.compile(r"[a-z\u00e0-\u00ff\u0153]+")
# A few very common words in ingredients lists, to tell French from English.
FRENCH_WORDS = {"et", "de", "du", "des", "sucre", "farine", "lait", "eau", "sel", "huile", "blé", "ou", "arôme", "arômes"}
ENGLISH_WORDS = {"and", "of", "sugar", "flour", "milk", "water", "salt", "oil", "wheat", "or", "flavour", "flavor"}
# Spanish, German, Italian, Dutch: OFF's "fr" field sometimes holds them (review round 2).
OTHER_WORDS = {"y", "con", "azúcar", "agua", "harina", "leche", "aceite", "sal", "trigo", "puede", "contener",
               "und", "mit", "zucker", "salz", "wasser", "mehl", "milch", "weizen", "kann",
               "zucchero", "acqua", "latte", "olio", "frumento", "può",
               "suiker", "zout", "melk", "tarwe", "het", "van", "bevat"}
SPACES = re.compile(r"\s+")


def clean(text):
    """Removes allergen markup (HTML tags and entities, _underscores_), keeps the words.
    Returns (clean text, had_markup)."""
    had_markup = ("<" in text and TAG.search(text) is not None) or UNDERSCORED.search(text) is not None
    text = html.unescape(TAG.sub("", text))
    text = UNDERSCORED.sub(r"\1", text)
    return SPACES.sub(" ", text).strip(), had_markup


def key_traces(text, traces):
    """The traces the text can answer: none if no "may contain"-like statement is printed."""
    return traces if TRACES_PRINTED.search(text) else []


def apply_key_fixes(records, counts):
    for r in records:
        fix = KEY_FIXES.get(r["code"])
        if fix:
            r.update({k: v for k, v in fix.items() if k != "why"})
            r["key_fixed"] = fix["why"]
            counts["6. answer key fixed by hand (KEY_FIXES)"] += 1


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
    other = sum(w in OTHER_WORDS for w in words)
    if other > french:
        return False
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
            if code in DROP:
                counts["   removed: unusable text (DROP)"] += 1
                continue
            counts["2. after junk and photographed products"] += 1

            allergens = sorted(a for a in (p["allergens_tags"] or []) if a in EU14)
            traces = sorted(a for a in (p["traces_tags"] or []) if a in EU14)
            for lang, bucket in (("fr", french), ("ar", arabic)):
                if lang not in texts:
                    continue
                text, had_markup = clean(texts[lang])
                if len(text) < 20:
                    continue
                if not right_language(text, lang):
                    counts[f"   {lang}: removed, text not really in {lang}"] += 1
                    continue
                if had_markup:
                    counts[f"   {lang}: allergen markup removed"] += 1
                printed = key_traces(text, traces)
                if traces and not printed:
                    counts[f"   {lang}: traces dropped from the key, not printed in the text"] += 1
                bucket.append((sort_key(code), {
                    "code": code, "lang": lang, "origin": "morocco", "text": text,
                    "allergens": allergens, "traces": printed, "traces_off": traces,
                    "had_markup": had_markup,
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
    apply_key_fixes(test + arabic_slice + train, counts)

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
    if "fr" not in texts or fake_barcode(code) or code.lstrip("0") in excluded_photos or code in DROP:
        return
    if set(p["categories_tags"] or []) & NON_FOOD:
        return
    key = negate(sort_key(code))
    if len(pool) >= FRANCE_POOL and key <= pool[0][0]:
        return  # pool full and this barcode sorts after all of them: skip the cleaning work
    text, had_markup = clean(texts["fr"])
    if len(text) < 20 or not right_language(text, "fr"):
        return
    traces = sorted(a for a in (p["traces_tags"] or []) if a in EU14)
    record = {
        "code": code, "lang": "fr", "origin": "france", "text": text,
        "allergens": sorted(a for a in (p["allergens_tags"] or []) if a in EU14),
        "traces": key_traces(text, traces), "traces_off": traces,
        "had_markup": had_markup,
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
        "allergens and traces (EU-14 tags from Open Food Facts; traces only when the text prints a",
        "\"may contain\"-like statement), traces_off (OFF's original traces), had_markup, and key_fixed",
        "(the reason) when the answer key was corrected by hand.",
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
    with_markup = sum(1 for r in test if r["had_markup"])
    fixed = sum(1 for r in test if r.get("key_fixed"))
    lines += [
        "",
        f"Test set: {no_allergen} products with no EU allergen, {with_traces} with traces, "
        f"{with_markup} had allergen markup (HTML or _underscores_, removed), {fixed} with a hand-fixed answer key.",
        "",
        "## Known problems",
        "",
        "- The answer key comes from Open Food Facts volunteers and automatic detection; no column says which.",
        "  Missed allergens exist (e.g. a cream \"de lait de vache\" tagged with none). Disagreements must be checked by hand.",
        "- Traces sometimes repeat the allergens themselves.",
        "- Only the reviewed products are fixed (KEY_FIXES): the rest of the key is still unchecked.",
        "  Day 6 reviews: 4 of 14 (round 1), then 67 of the 89 products the best setup got wrong had a key error or an unusable text (round 2).",
        "  Only disagreements were reviewed, so errors the key shares with every setup remain.",
        "- Non-food and fake-barcode filters are heuristics.",
    ]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    main()
