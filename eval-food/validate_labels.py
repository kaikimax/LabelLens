"""
Checks labels.json for mistakes before it's used as the answer key.

ERRORS   = the file is wrong and must be fixed (bad format, unknown tag, missing barcode...).
WARNINGS = something looks suspicious; check it against the package, then leave it if it's really printed that way.

Usage (from the project folder):
    python eval-food/validate_labels.py
    python eval-food/validate_labels.py path/to/labels.json
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).parent
DEFAULT_PATH = HERE / "data" / "photos" / "labels.json"
IMAGES_DIR = HERE / "data" / "photos" / "images"

TYPES = {"GENERAL", "DRINK", "CHEESE", "OIL"}
LANGUAGES = {"fr", "ar", "en"}
GRADES = {"A", "B", "C", "D", "E", None}
SITUATIONS = {
    "arabic", "french-only", "round-packaging", "shiny-packaging", "flat-packaging",
    "blurry", "glare", "angled", "hand-in-photo", "small-text",
    "per-portion-converted", "printed-as-less-than",
}
ALLERGENS = {
    "en:milk", "en:gluten", "en:soybeans", "en:eggs", "en:nuts", "en:mustard",
    "en:fish", "en:peanuts", "en:celery", "en:sesame-seeds",
    "en:sulphur-dioxide-and-sulphites", "en:crustaceans", "en:molluscs", "en:lupin",
}
NUTRIENTS = ["energy_kj", "fat", "saturated_fat", "carbohydrates", "sugars", "fibre", "protein", "salt"]
PRODUCT_KEYS = {
    "id", "photos", "situations", "barcode", "name", "brand", "type", "per_100g",
    "ingredients_text", "ingredients_language", "allergens", "may_contain",
    "printed_grade", "notes",
}

# Simple word hints: if the ingredients mention one of these words, the allergen
# is probably present. Only a hint: it produces a warning, never an error.
ALLERGEN_HINTS = {
    "en:gluten": ["blé", "ble ", "farine", "orge", "seigle", "avoine", "épeautre", "gluten", "malt",
                  "wheat", "barley", "rye", "oat", "قمح", "شعير"],
    "en:milk": ["lait", "lactos", "beurre", "crème", "creme", "fromage", "caséine", "milk", "butter",
                "cream", "cheese", "whey", "حليب"],
    "en:eggs": ["œuf", "oeuf", "egg", "بيض"],
    "en:peanuts": ["arachide", "cacahu", "peanut", "فول سوداني"],
    "en:nuts": ["amande", "noisette", "noix", "pistache", "cajou", "almond", "hazelnut", "walnut",
                "cashew", "pistachio", "لوز", "بندق"],
    "en:sesame-seeds": ["sésame", "sesame", "سمسم"],
    "en:mustard": ["moutarde", "mustard", "خردل"],
    "en:fish": ["poisson", "thon", "sardine", "anchois", "saumon", "fish", "tuna", "سمك"],
    "en:celery": ["céleri", "celeri", "celery"],
    "en:sulphur-dioxide-and-sulphites": ["sulfite", "sulphite", "bisulfite", "métabisulfite",
                                         "anhydride sulfureux", "e220", "e221", "e222", "e223",
                                         "e224", "e226", "e227", "e228", "ميتابيسلفيت"],
}


def is_number(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def check_product(p, errors, warnings):
    pid = p.get("id", "<no id>")

    def err(msg):
        errors.append(f"{pid}: {msg}")

    def warn(msg):
        warnings.append(f"{pid}: {msg}")

    missing = PRODUCT_KEYS - p.keys()
    extra = p.keys() - PRODUCT_KEYS
    if missing:
        err(f"missing fields {sorted(missing)}")
    if extra:
        err(f"unknown fields {sorted(extra)} (e.g. per_100ml must be per_100g)")

    if not p.get("barcode"):
        err("barcode is empty: needed to keep this product out of the training data")
    elif not str(p["barcode"]).isdigit():
        err(f"barcode '{p['barcode']}' must contain digits only")

    if p.get("type") not in TYPES:
        err(f"type '{p.get('type')}' must be one of {sorted(TYPES)}")
    if p.get("ingredients_language") not in LANGUAGES:
        err(f"ingredients_language '{p.get('ingredients_language')}' must be one of {sorted(LANGUAGES)}")
    if p.get("printed_grade") not in GRADES:
        err(f"printed_grade '{p.get('printed_grade')}' must be A-E or null")

    for s in p.get("situations", []):
        if s not in SITUATIONS:
            err(f"unknown situation '{s}'")

    for field in ("allergens", "may_contain"):
        for a in p.get(field, []):
            if a not in ALLERGENS:
                err(f"{field}: unknown tag '{a}' (use the en:... tags from DATA_PLAN.md)")

    photos = p.get("photos", {})
    for role in ("nutrition", "ingredients"):
        if not photos.get(role):
            err(f"photos.{role} is empty")
    if IMAGES_DIR.exists():
        for role, name in photos.items():
            if name and not (IMAGES_DIR / name).exists():
                warn(f"photo file not found: images/{name}")

    values = p.get("per_100g", {})
    for n in NUTRIENTS:
        if n not in values:
            err(f"per_100g.{n} is missing (use null if not printed)")
            continue
        v = values[n]
        if v is not None and not is_number(v):
            err(f"per_100g.{n} = {v!r} must be a number or null (write '<0.5' as 0.5 + situation printed-as-less-than)")
        elif is_number(v) and (v < 0 or (n != "energy_kj" and v > 100)):
            err(f"per_100g.{n} = {v} is out of range")

    text = p.get("ingredients_text", "")
    if text.strip().lower().startswith(("ingrédients", "ingredients", "ingredient", "المكونات")):
        err("ingredients_text starts with the heading: start at the first ingredient")
    if any(word in text.lower() for word in ("peut contenir", "may contain", "traces")):
        warn("ingredients_text contains a 'may contain' sentence: move it to may_contain")

    # --- Sanity checks on the numbers (warnings) ---
    def num(name):
        v = values.get(name)
        return v if is_number(v) else None

    sugars, carbs = num("sugars"), num("carbohydrates")
    if sugars is not None and carbs is not None and sugars > carbs + 0.05:
        warn(f"sugars ({sugars}) > carbohydrates ({carbs})")
    sat, fat = num("saturated_fat"), num("fat")
    if sat is not None and fat is not None and sat > fat + 0.05:
        warn(f"saturated_fat ({sat}) > fat ({fat})")

    energy = num("energy_kj")
    # Skip the energy check when the typed values are approximate by nature:
    # - printed-as-less-than: "<0.5" is stored as 0.5, an upper bound, so the macro sum is wrong;
    # - per-portion-converted: printed values are rounded per serving, and multiplying
    #   them (x20 for a 5 ml serving) multiplies the rounding error too.
    approximate = any(s in p.get("situations", [])
                      for s in ("printed-as-less-than", "per-portion-converted"))
    if energy is not None and fat is not None and carbs is not None and not approximate:
        protein = num("protein") or 0
        fibre = num("fibre") or 0
        expected = protein * 17 + carbs * 17 + fat * 37 + fibre * 8
        if expected > 40 or energy > 40:  # ignore near-zero products (water, salt...)
            diff = abs(energy - expected) / max(energy, expected)
            if diff > 0.20:
                warn(f"energy {energy} kJ but macros add up to ~{expected:.0f} kJ "
                     f"({diff:.0%} apart): check the energy unit (kcal vs kJ) and the values")

    # --- Allergen hints (warnings) ---
    lower = text.lower()
    declared = set(p.get("allergens", [])) | set(p.get("may_contain", []))
    for tag, words in ALLERGEN_HINTS.items():
        if tag not in declared and any(w in lower for w in words):
            hit = next(w for w in words if w in lower)
            warn(f"ingredients mention '{hit}' but {tag} isn't listed: check it")


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PATH
    data = json.loads(path.read_text(encoding="utf-8"))
    products = data.get("products", [])

    errors, warnings = [], []
    seen_ids, seen_barcodes = set(), set()
    for p in products:
        pid, bc = p.get("id"), p.get("barcode")
        if pid in seen_ids:
            errors.append(f"{pid}: duplicate id")
        seen_ids.add(pid)
        if bc:
            if bc in seen_barcodes:
                errors.append(f"{pid}: duplicate barcode {bc} (same product twice?)")
            seen_barcodes.add(bc)
        check_product(p, errors, warnings)

    # Summary of the mix, compared with the targets in DATA_PLAN.md
    def count(pred):
        return sum(1 for p in products if pred(p))

    print(f"{len(products)} products in {path}\n")
    print("Mix vs targets:")
    print(f"  products                 {len(products):>3} / 40")
    print(f"  with milk                {count(lambda p: 'en:milk' in p.get('allergens', [])):>3} / 10+")
    print(f"  with gluten              {count(lambda p: 'en:gluten' in p.get('allergens', [])):>3} / 10+")
    print(f"  no allergens             {count(lambda p: not p.get('allergens')):>3} / 5+")
    print(f"  with Arabic on label     {count(lambda p: 'arabic' in p.get('situations', [])):>3} / 15+")
    print(f"  drink / cheese / oil     {count(lambda p: p.get('type') in ('DRINK', 'CHEESE', 'OIL')):>3} / max 10")
    bad = ("blurry", "glare", "angled")
    print(f"  bad photos               {count(lambda p: any(s in p.get('situations', []) for s in bad)):>3} / 10")

    print(f"\n{len(errors)} error(s):")
    for e in errors:
        print("  ERROR  ", e)
    print(f"\n{len(warnings)} warning(s):")
    for w in warnings:
        print("  WARN   ", w)

    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
