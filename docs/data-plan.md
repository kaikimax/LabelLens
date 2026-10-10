# LabelLens — Data Plan

Two sources of data, with different jobs:

| Source | Size | Used for | Trust level |
|---|---|---|---|
| **My photographed products** | 40 | Extraction eval (E1), end-to-end grade (E2b), hand-checked tagging eval (E3) | Highest: I verify every value against the package. Never used for training. |
| **Open Food Facts (OFF) export** | ~5,000 for formula checks; ~3,000 train + 500 test for tagging | Nutri-Score formula check (E2a), fine-tuning, tagging eval (E3) | Medium: volunteer-entered, some tags parser-generated. |

## 1. Photographing products

### What to photograph
Aim for **40 products**, spread so the breakdowns mean something:

| Mix | Target |
|---|---|
| Label language | ≥ 15 with Arabic text on the nutrition table or ingredients, ≥ 15 French-only |
| Packaging | ≥ 10 curved (bottles, cans, cups), ≥ 10 shiny/plastic, the rest flat cardboard |
| Category | ≥ 30 general foods (biscuits, cereals, jams, yogurts, snacks, sauces, pasta); ≤ 10 beverages/cheese/oils, to test the `NOT_SCORED` path |
| Allergens | ≥ 10 with milk, ≥ 10 with gluten, ≥ 3 with nuts or peanuts, ≥ 5 with none |
| Additives | ≥ 15 with 3+ E-numbers, ≥ 5 with none |

Good sources: your own kitchen, a family member's, and one Marjane/Carrefour/BIM trip. Photograph in the shop only if allowed; buying cheap items is simpler.

### How to photograph
- 3 photos per product: front, nutrition table, ingredients list.
- Mostly normal phone photos as a real user would take them. **Do not** retake bad ones for 10 of the 40: blur, glare and angle are part of the test.
- Name files `p001_front.jpg`, `p001_nutrition.jpg`, `p001_ingredients.jpg`.
- Strip location metadata before committing: `exiftool -all= *.jpg`.

### Labelling (`eval-food/data/photos/labels.json`)
About 2–3 minutes per product. One entry per product:

```json
{
  "id": "p001",
  "tags": ["lang=fr+ar", "packaging=curved", "photo=blurry"],
  "barcode": "6111000000000",
  "name": "…",
  "category": "GENERAL",
  "nutrients_per_100g": {
    "energy_kj": 1980, "sugars": 28.0, "sat_fat": 6.5,
    "salt": 0.45, "fibre": 2.1, "protein": 6.8
  },
  "ingredients_text": "Farine de blé, sucre, huile de palme, …",
  "allergens": ["en:gluten", "en:milk"],
  "additives": ["en:e322", "en:e500"],
  "expected_grade": "D"
}
```

Rules:
- Copy values **exactly as printed**, per 100 g. If only "per portion" is printed, convert and add the tag `per_portion_converted`.
- `expected_grade`: compute it once with a reference Nutri-Score calculator, or take it from the package if printed. Note the source in a `grade_source` field.
- Allergens and additives: tag what is in the ingredients text, including "may contain traces of" as a separate field `traces` (excluded from v1 metrics, but recorded).
- Record the barcode: it lets you check your photo products against OFF later and makes sure none of them leak into training.

## 2. Open Food Facts export

### Getting it
- Download the OFF product export (Parquet or JSONL; prefer Parquet, it's much smaller to filter). OFF also publishes it as a dataset on Hugging Face.
- Licence: database under ODbL, images under CC BY-SA. Attribution goes in the README and UI footer.
- Never commit the full export. Commit only the filtered, versioned subsets with a data card (see section 4).

### Fields to keep

| Field | Used for |
|---|---|
| `code` | Barcode: dedup and train/test split |
| `product_name`, `brands`, `countries_tags`, `lang` | Filtering, breakdowns |
| `categories_tags` | Detecting beverages/cheese/fats for the `NOT_SCORED` rule |
| `ingredients_text`, `ingredients_text_fr`, `ingredients_text_ar` | Tagging input |
| `allergens_tags`, `traces_tags` | Tagging labels |
| `additives_tags` | Tagging labels |
| `nutriments`: `energy-kj_100g`, `sugars_100g`, `saturated-fat_100g`, `salt_100g`, `fiber_100g`, `proteins_100g`, fruit/vegetable estimate | Formula check |
| `nutriscore_grade`, `nutriscore_score` (and the version fields; see below) | Reference grade |
| `states_tags`, `data_quality_errors_tags` | Quality filtering |

**Check before E2a:** OFF has migrated to the 2023 Nutri-Score algorithm. Confirm which fields hold the 2023 grade versus the older one, and compare against the same version you implement.

### Filtering
Keep a product only if:
- it has a non-empty ingredients text of at least 20 characters,
- `states_tags` marks ingredients as completed,
- it has no `data_quality_errors_tags` related to nutrition or ingredients,
- for the formula subset: all six core nutrients are present and a reference grade exists.

Prefer French-language products and Moroccan ones (`en:morocco`) where possible, so the training text resembles your test labels. Expect few Moroccan products with complete data; mix in French ones.

### Splits

| Subset | Size | Purpose |
|---|---|---|
| `formula_check` | ~5,000 | E2a. Not used for any model. |
| `tagging_train` | ~3,000 | Fine-tuning |
| `tagging_val` | ~300 | Choosing the fine-tuning epoch / checkpoint |
| `tagging_test` | 500 | E3. Touched only for final numbers. |

- Split **by barcode**, deterministically (hash of the barcode), so reruns produce the same split.
- Remove any barcode that appears among your 40 photographed products from all OFF subsets.
- Remove near-duplicates: products whose normalized ingredients text is identical, keeping one, so the test set doesn't contain copies of training examples under different barcodes.

### Fine-tuning format
One JSONL line per product, chat format:

```json
{"messages": [
  {"role": "system", "content": "Extract allergens and additives from the ingredients list. Answer with JSON: {\"allergens\": [...], \"additives\": [...]} using Open Food Facts tag codes."},
  {"role": "user", "content": "Farine de blé, sucre, lait écrémé en poudre, émulsifiant: lécithine de soja, …"},
  {"role": "assistant", "content": "{\"allergens\": [\"en:gluten\", \"en:milk\", \"en:soybeans\"], \"additives\": [\"en:e322\"]}"}
]}
```

Keep the system prompt identical between training and inference, and identical for the prompt-only baselines, so the comparison is fair.

## 3. Additives knowledge base (RAG)

- Source: the OFF additives taxonomy, which gives each E-number's name, function class, and links to official evaluations.
- One chunk per additive, keyed by its tag ID (e.g. `en:e330`), so citation correctness (E4a) can be checked automatically.
- Store name, function class, and a short description. If an entry has no description, the explanation must say so rather than invent one; that behaviour is part of the groundedness eval.

## 4. Data cards

Every committed subset gets a short `DATA_CARD.md` next to it:
- source and export date,
- filters applied and counts after each filter,
- split method and seed,
- known issues (e.g. parser-generated tags, language mix),
- licence.

This takes 10 minutes per subset and is one of the clearest signals of seriousness in an ML repo.

## 5. Day 1 checklist

- [ ] Photograph the first 20 products (3 photos each).
- [ ] Label 5 of them in `labels.json` to test the format before doing all 40.
- [ ] Download the OFF Parquet export and check its size and fields.
- [ ] Confirm which OFF fields hold the 2023 Nutri-Score grade.